package tw.testa.unit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.common.ApiException;
import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;

/**
 * Units and unit members.
 * Creating and editing units: system administrators. Managing members (assigning roles): system administrators or the unit's owner.
 */
@RestController
@RequestMapping("/api/units")
public class UnitController {
    private final UnitRepository unitRepo;
    private final UnitRoleRepository roleRepo;
    private final StaffRepository staffRepo;
    private final AuditService audit;
    private final AccessGuard access;

    public UnitController(UnitRepository unitRepo, UnitRoleRepository roleRepo, StaffRepository staffRepo,
            AuditService audit, AccessGuard access) {
        this.unitRepo = unitRepo;
        this.roleRepo = roleRepo;
        this.staffRepo = staffRepo;
        this.audit = audit;
        this.access = access;
    }

    public record UnitDto(Long id, String name, UnitType type, boolean active, Instant createdAt) {}

    public record CreateRequest(@NotBlank(message = "請輸入單位名稱。") @Size(max = 100, message = "單位名稱太長了。") String name,
            @NotNull(message = "請選擇單位類型。") UnitType type) {}

    public record UpdateRequest(@Size(min = 1, max = 100, message = "請輸入單位名稱。") String name, Boolean active) {}

    public record MemberDto(Long id, Long staffId, String email, String name, boolean active, Role role) {}

    public record AddMemberRequest(@NotBlank(message = "請輸入教職員的 Email。") String email,
            @NotNull(message = "請選擇角色。") Role role) {}

    /** System administrators see every unit; others only units where they hold a role. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<UnitDto> list() {
        if (access.isSystemAdmin()) {
            return unitRepo.findAll(Sort.by("name")).stream().map(UnitController::toDto).toList();
        }
        return roleRepo.findByStaffIdWithUnit(access.current().id()).stream().map(UnitRole::getUnit)
                .filter(Unit::isActive).distinct().map(UnitController::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.isSystemAdmin()")
    @Transactional
    public UnitDto create(@Valid @RequestBody CreateRequest body) {
        String name = body.name().trim();
        if (unitRepo.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("name_taken", "已經有同名的單位了。");
        }
        Unit u = unitRepo.save(new Unit(name, body.type()));
        audit.record("UNIT_CREATED", "unit", u.getId(), u.getId(), null, name);
        return toDto(u);
    }

    @PatchMapping("/{unitId}")
    @PreAuthorize("@access.isSystemAdmin()")
    @Transactional
    public UnitDto update(@PathVariable Long unitId, @Valid @RequestBody UpdateRequest body) {
        Unit u = find(unitId);
        if (body.name() != null && !body.name().trim().equalsIgnoreCase(u.getName())) {
            String name = body.name().trim();
            if (unitRepo.existsByNameIgnoreCase(name)) {
                throw ApiException.conflict("name_taken", "已經有同名的單位了。");
            }
            audit.record("UNIT_RENAMED", "unit", u.getId(), u.getId(), null, u.getName() + " → " + name);
            u.setName(name);
        }
        if (body.active() != null && body.active() != u.isActive()) {
            u.setActive(body.active());
            audit.record(body.active() ? "UNIT_ENABLED" : "UNIT_DISABLED", "unit", u.getId(), u.getId(), null, null);
        }
        return toDto(u);
    }

    @GetMapping("/{unitId}/members")
    @PreAuthorize("@access.canManageUnit(#unitId)")
    @Transactional(readOnly = true)
    public List<MemberDto> members(@PathVariable Long unitId) {
        find(unitId);
        return roleRepo.findByUnitIdWithStaff(unitId).stream().map(UnitController::toDto).toList();
    }

    @PostMapping("/{unitId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.canManageUnit(#unitId)")
    @Transactional
    public MemberDto addMember(@PathVariable Long unitId, @Valid @RequestBody AddMemberRequest body) {
        Unit u = find(unitId);
        Staff s = staffRepo.findByEmail(Staff.normalizeEmail(body.email())).filter(Staff::isActive)
                .orElseThrow(() -> ApiException.badRequest("unknown_staff", "找不到這個 Email 的教職員帳號，請先請系統管理員建立。"));
        if (roleRepo.existsByUnitIdAndStaffIdAndRole(unitId, s.getId(), body.role())) {
            throw ApiException.conflict("already_member", "這位教職員已經有這個角色了。");
        }
        UnitRole r = roleRepo.save(new UnitRole(u, s, body.role()));
        audit.record("UNIT_ROLE_GRANTED", "staff", s.getId(), unitId, null, body.role().name());
        return toDto(r);
    }

    @DeleteMapping("/{unitId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@access.canManageUnit(#unitId)")
    @Transactional
    public void removeMember(@PathVariable Long unitId, @PathVariable Long memberId) {
        UnitRole r = roleRepo.findById(memberId).filter(x -> x.getUnit().getId().equals(unitId))
                .orElseThrow(() -> ApiException.notFound("找不到這筆角色指派。"));
        // An owner cannot leave the unit without an owner; a system administrator handles hand-overs
        if (r.getRole() == Role.OWNER && !access.isSystemAdmin()
                && roleRepo.countByUnitIdAndRole(unitId, Role.OWNER) <= 1) {
            throw ApiException.badRequest("last_owner", "單位至少要有一位負責人。");
        }
        audit.record("UNIT_ROLE_REVOKED", "staff", r.getStaff().getId(), unitId, null, r.getRole().name());
        roleRepo.delete(r);
    }

    private Unit find(Long id) {
        return unitRepo.findById(id).orElseThrow(() -> ApiException.notFound("找不到這個單位。"));
    }

    private static UnitDto toDto(Unit u) {
        return new UnitDto(u.getId(), u.getName(), u.getType(), u.isActive(), u.getCreatedAt());
    }

    private static MemberDto toDto(UnitRole r) {
        Staff s = r.getStaff();
        return new MemberDto(r.getId(), s.getId(), s.getEmail(), s.getName(), s.isActive(), r.getRole());
    }
}
