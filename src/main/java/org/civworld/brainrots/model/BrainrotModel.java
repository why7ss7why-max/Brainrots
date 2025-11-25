    package org.civworld.brainrots.model;

    import lombok.Getter;
    import lombok.Setter;
    import org.civworld.brainrots.type.Modificator;
    import org.civworld.brainrots.type.Rarity;

    @Getter @Setter public class BrainrotModel {
        private final String id;
        private final String displayName;
        private final Rarity rarity;
        private final int cost;
        private Modificator modificator = Modificator.BRONZE;
        private final int earn;
        private double widthHitbox = 0.6;
        private double heightHitbox = 1.8;
        private double marginHologram = 0;

        public BrainrotModel(String id, String displayName, Rarity rarity, int cost, int earn){
            this.id = id;
            this.displayName = displayName;
            this.rarity = rarity;
            this.cost = cost;
            this.earn = earn;
        }
    }