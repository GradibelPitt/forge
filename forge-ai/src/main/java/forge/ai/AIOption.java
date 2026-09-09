package forge.ai;

public enum AIOption {
    USE_HYBRID_SIMULATION,
    USE_FULL_SIMULATION;

    /** Source compatibility for the preserved DIY desktop's simulation toggle. */
    public static final AIOption USE_SIMULATION = USE_FULL_SIMULATION;
}
