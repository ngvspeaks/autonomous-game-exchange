package com.simulation.model;

/**
 * Key map locations for agent pathfinding and economic activities.
 */
public enum MapZone {
    LUMBER_MILL(100.0, 100.0),
    MINE(700.0, 100.0),
    GRAND_EXCHANGE(400.0, 300.0),
    GUILD(400.0, 500.0);

    private final double x;
    private final double y;

    MapZone(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    /**
     * Calculates the Euclidean distance from a given point to this zone.
     */
    public double distanceTo(double fromX, double fromY) {
        return Math.hypot(this.x - fromX, this.y - fromY);
    }
}
