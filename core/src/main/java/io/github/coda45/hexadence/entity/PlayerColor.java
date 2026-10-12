package io.github.coda45.hexadence.entity;

public enum PlayerColor {
    CYAN("cyan"),
    RED("red"),
    GREEN("green"),
    GOLD("gold");

    private final String assetFolder;

    PlayerColor(String assetFolder) {
        this.assetFolder = assetFolder;
    }

    public String assetFolder() {
        return assetFolder;
    }
}