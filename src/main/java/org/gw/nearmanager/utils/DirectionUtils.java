package org.gw.nearmanager.utils;

public final class DirectionUtils {

    private DirectionUtils() {}

    public static String getDirectionKey(double viewerX, double viewerZ, float viewerYaw, double toX, double toZ) {
        double dx = toX - viewerX;
        double dz = toZ - viewerZ;

        float yaw = viewerYaw;
        yaw = ((yaw % 360) + 360) % 360;

        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        angle = ((angle % 360) + 360) % 360;

        double relative = angle - yaw;
        relative = ((relative % 360) + 360) % 360;

        if (relative < 22.5 || relative >= 337.5) return "north";
        if (relative < 67.5) return "northeast";
        if (relative < 112.5) return "east";
        if (relative < 157.5) return "southeast";
        if (relative < 202.5) return "south";
        if (relative < 247.5) return "southwest";
        if (relative < 292.5) return "west";
        return "northwest";
    }
}