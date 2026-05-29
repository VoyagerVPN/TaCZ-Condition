package ru.ranazy.condition.geometry;

import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Класс, представляющий ориентированный (повернутый) хитбокс (OBB)
 * на основе локального AABB куба модели и матрицы трансформации кости.
 */
public class RotatedHitbox {
    public final AABB localBounds;
    public final Matrix4f transformMatrix;
    public final Matrix4f inverseMatrix;
    public final Vec3 worldOffset;
    public final EnumPlayerPart bodyPart;

    public RotatedHitbox(AABB localBounds, Matrix4f transformMatrix, Vec3 worldOffset, EnumPlayerPart bodyPart) {
        this.localBounds = localBounds;
        this.transformMatrix = new Matrix4f(transformMatrix);
        // Вычисляем обратную матрицу для перевода координат луча в локальное пространство OBB
        this.inverseMatrix = new Matrix4f(transformMatrix).invert();
        this.worldOffset = worldOffset;
        this.bodyPart = bodyPart;
    }

    /**
     * Вычисляет пересечение луча (траектории пули) с повернутым хитбоксом.
     * 
     * @param rayStart Начало луча в мировых координатах
     * @param rayEnd Конец луча в мировых координатах
     * @return Точка попадания в мировых координатах, если пересечение произошло
     */
    public Optional<Vec3> intersect(Vec3 rayStart, Vec3 rayEnd) {
        // Переводим луч в координаты относительно центра сущности
        Vec3 relStart = rayStart.subtract(this.worldOffset);
        Vec3 relEnd = rayEnd.subtract(this.worldOffset);

        // Преобразуем координаты в локальное пространство OBB с помощью обратной матрицы
        Vector3f localStart = new Vector3f((float) relStart.x, (float) relStart.y, (float) relStart.z);
        Vector3f localEnd = new Vector3f((float) relEnd.x, (float) relEnd.y, (float) relEnd.z);
        this.inverseMatrix.transformPosition(localStart);
        this.inverseMatrix.transformPosition(localEnd);

        Vec3 boxStart = new Vec3(localStart.x, localStart.y, localStart.z);

        // Если точка начала луча уже находится внутри хитбокса
        if (this.localBounds.contains(boxStart)) {
            return Optional.of(rayStart);
        }

        Vec3 boxEnd = new Vec3(localEnd.x, localEnd.y, localEnd.z);
        
        // Выполняем стандартный быстрый Raycast в локальном пространстве AABB
        Optional<Vec3> hitResult = this.localBounds.clip(boxStart, boxEnd);

        // Если произошло пересечение, переводим полученную точку обратно в мировые координаты
        if (hitResult.isPresent()) {
            Vector3f worldHit = new Vector3f((float) hitResult.get().x, (float) hitResult.get().y, (float) hitResult.get().z);
            this.transformMatrix.transformPosition(worldHit);
            return Optional.of(new Vec3(worldHit.x, worldHit.y, worldHit.z).add(this.worldOffset));
        }

        return Optional.empty();
    }
}
