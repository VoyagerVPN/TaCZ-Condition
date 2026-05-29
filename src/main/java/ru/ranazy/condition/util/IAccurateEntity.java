package ru.ranazy.condition.util;

import ru.ranazy.condition.geometry.RotatedHitbox;

import java.util.List;

/**
 * Интерфейс-расширение, который внедряется в базовый класс Entity
 * для динамического хранения списка ориентированных хитбоксов OBB на клиенте и сервере.
 */
public interface IAccurateEntity {
    List<RotatedHitbox> condition$getHitboxes();
    void condition$addHitbox(RotatedHitbox obb);
    void condition$clearHitboxes();
}
