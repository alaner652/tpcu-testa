package tw.testa.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PageDto<T>(List<T> items, int page, int size, long total) {
    public static <E, T> PageDto<T> of(Page<E> p, Function<E, T> map) {
        return new PageDto<>(p.getContent().stream().map(map).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }
}
