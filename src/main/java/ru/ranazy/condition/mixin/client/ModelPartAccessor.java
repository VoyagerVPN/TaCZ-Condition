package ru.ranazy.condition.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * Аксессор для получения приватных полей кубоидов и дочерних элементов ванильного класса ModelPart.
 */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> condition$getCubes();

    @Accessor("children")
    Map<String, ModelPart> condition$getChildren();
}
