package com.mdominguez.ietiParkAppMobil;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;

public final class DebugOverlay implements Disposable {

    private static final float ZONE_FILL_ALPHA = 0.20f;
    private static final float ZONE_STROKE_ALPHA = 0.85f;
    private static final float PATH_ALPHA = 0.90f;
    private static final float PATH_POINT_RADIUS = 2.2f;
    private static final int CIRCLE_SEGMENTS = 12;

    private final ShapeRenderer shapes = new ShapeRenderer();

    public void render(
        LevelData level,
        OrthographicCamera camera,
        boolean showZones,
        boolean showPaths,
        com.badlogic.gdx.utils.Array<RuntimeTransform> zoneRuntimeStates
    ) {
        if (level == null || (!showZones && !showPaths)) {
            return;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);

        if (showZones) {
            renderZones(level, zoneRuntimeStates);
        }
        if (showPaths) {
            renderPaths(level);
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderZones(LevelData level, com.badlogic.gdx.utils.Array<RuntimeTransform> zoneRuntimeStates) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < level.zones.size; i++) {
            LevelData.LevelZone zone = level.zones.get(i);
            RuntimeTransform runtime = zoneRuntimeStates != null && i >= 0 && i < zoneRuntimeStates.size
                ? zoneRuntimeStates.get(i)
                : null;
            float zoneX = runtime == null ? zone.x : runtime.x;
            float zoneY = runtime == null ? zone.y : runtime.y;
            float yUp = toYUp(level.worldHeight, zoneY, zone.height);
            shapes.setColor(zone.color.r, zone.color.g, zone.color.b, ZONE_FILL_ALPHA);
            shapes.rect(zoneX, yUp, zone.width, zone.height);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < level.zones.size; i++) {
            LevelData.LevelZone zone = level.zones.get(i);
            RuntimeTransform runtime = zoneRuntimeStates != null && i >= 0 && i < zoneRuntimeStates.size
                ? zoneRuntimeStates.get(i)
                : null;
            float zoneX = runtime == null ? zone.x : runtime.x;
            float zoneY = runtime == null ? zone.y : runtime.y;
            float yUp = toYUp(level.worldHeight, zoneY, zone.height);
            shapes.setColor(zone.color.r, zone.color.g, zone.color.b, ZONE_STROKE_ALPHA);
            shapes.rect(zoneX, yUp, zone.width, zone.height);
        }
        shapes.end();
    }

    private void renderPaths(LevelData level) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < level.paths.size; i++) {
            LevelData.LevelPath path = level.paths.get(i);
            shapes.setColor(path.color.r, path.color.g, path.color.b, PATH_ALPHA);

            for (int p = 0; p + 1 < path.points.size; p++) {
                Vector2 a = path.points.get(p);
                Vector2 b = path.points.get(p + 1);
                shapes.line(a.x, toYUp(level.worldHeight, a.y), b.x, toYUp(level.worldHeight, b.y));
            }

            for (int p = 0; p < path.points.size; p++) {
                Vector2 point = path.points.get(p);
                shapes.circle(point.x, toYUp(level.worldHeight, point.y), PATH_POINT_RADIUS, CIRCLE_SEGMENTS);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < level.paths.size; i++) {
            LevelData.LevelPath path = level.paths.get(i);
            if (path.points.size <= 0) {
                continue;
            }
            Vector2 first = path.points.first();
            Vector2 last = path.points.peek();
            shapes.setColor(0.20f, 1.00f, 0.20f, 0.95f);
            shapes.circle(first.x, toYUp(level.worldHeight, first.y), PATH_POINT_RADIUS + 0.9f, CIRCLE_SEGMENTS);
            shapes.setColor(1.00f, 0.20f, 0.20f, 0.95f);
            shapes.circle(last.x, toYUp(level.worldHeight, last.y), PATH_POINT_RADIUS + 0.9f, CIRCLE_SEGMENTS);
        }
        shapes.end();
    }

    private float toYUp(float worldHeight, float yDown) {
        return worldHeight - yDown;
    }

    private float toYUp(float worldHeight, float yDown, float height) {
        return worldHeight - yDown - height;
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
