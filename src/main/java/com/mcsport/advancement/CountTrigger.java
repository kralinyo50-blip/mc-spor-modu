package com.mcsport.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * One reusable trigger for every McSport numeric advancement condition
 * (goals scored, wins, cards, team joins). Advancement JSON supplies the
 * required minimum in {@code conditions.count}; the server fires the trigger
 * with the player's running total for that event.
 */
public class CountTrigger extends SimpleCriterionTrigger<CountTrigger.Instance> {

    private final ResourceLocation id;

    public CountTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player,
                                      DeserializationContext context) {
        int required = GsonHelper.getAsInt(json, "count", 1);
        return new Instance(id, player, required);
    }

    public void trigger(ServerPlayer player, int total) {
        this.trigger(player, instance -> instance.matches(total));
    }

    public static class Instance extends AbstractCriterionTriggerInstance {

        private final int required;

        public Instance(ResourceLocation id, ContextAwarePredicate player, int required) {
            super(id, player);
            this.required = Math.max(1, required);
        }

        public boolean matches(int total) {
            return total >= required;
        }
    }
}
