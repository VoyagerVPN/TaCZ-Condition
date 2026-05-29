package ru.ranazy.condition.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.ranazy.condition.geometry.RotatedHitbox;
import ru.ranazy.condition.util.IAccurateEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Миксин для расширения ванильного класса Entity полем для хранения списка повернутых хитбоксов.
 */
@Mixin(Entity.class)
public class EntityMixin implements IAccurateEntity {
    @Unique
    private final List<RotatedHitbox> condition$accurateHitboxes = new ArrayList<>();

    @Override
    public List<RotatedHitbox> condition$getHitboxes() {
        return this.condition$accurateHitboxes;
    }

    @Override
    public void condition$addHitbox(RotatedHitbox obb) {
        this.condition$accurateHitboxes.add(obb);
    }

    @Override
    public void condition$clearHitboxes() {
        this.condition$accurateHitboxes.clear();
    }
}
