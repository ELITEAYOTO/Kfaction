package me.krunsh.kfaction.progression;

/** Vérifications runtime optionnelles pour mondes, régions et intégrations. */
public interface ValidationEnvironment {
    enum Status { VALID, INVALID, UNKNOWN }

    Status worldExists(String world);
    Status regionExists(String world, String region);
    Status customItemExists(String itemId);
    Status kcraftRecipeExists(String recipeId);

    ValidationEnvironment PERMISSIVE = new ValidationEnvironment() {
        @Override public Status worldExists(String world) { return Status.UNKNOWN; }
        @Override public Status regionExists(String world, String region) { return Status.UNKNOWN; }
        @Override public Status customItemExists(String itemId) { return Status.UNKNOWN; }
        @Override public Status kcraftRecipeExists(String recipeId) { return Status.UNKNOWN; }
    };
}
