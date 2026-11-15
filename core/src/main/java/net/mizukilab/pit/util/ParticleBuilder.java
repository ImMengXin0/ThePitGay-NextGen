package net.mizukilab.pit.util;

import org.bukkit.Particle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;

public class ParticleBuilder {
    Location location;
    Particle particle;
    float offsetX;
    float offsetY;
    float offsetZ;
    float velocity;
    int count;

    public ParticleBuilder(Location location, Particle particle) {
        this.location = location;
        this.particle = particle;
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;
        this.offsetZ = 0.0f;
        this.velocity = 0.0f;
        this.count = 1;
    }

    public void play() {
        Object data = particle == Particle.DUST ? new Particle.DustOptions(Color.RED, 1) : null;
        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, velocity, data);
    }

    public ParticleBuilder setOffsetX(float offsetX) {
        this.offsetX = offsetX;
        return this;
    }

    public ParticleBuilder setOffsetY(float offsetY) {
        this.offsetY = offsetY;
        return this;
    }

    public ParticleBuilder setOffsetZ(float offsetZ) {
        this.offsetZ = offsetZ;
        return this;
    }

    public ParticleBuilder setVelocity(float velocity) {
        this.velocity = velocity;
        return this;
    }

    public ParticleBuilder setCount(int count) {
        this.count = count;
        return this;
    }

    public static void playColored(Location loc, Color color, Particle ep) {
        Object data = ep == Particle.DUST ? new Particle.DustOptions(color, 1) : color;
        loc.getWorld().spawnParticle(ep, loc, 1, 0, 0, 0, 0, data);
    }

    public static void playColoredDust(Location loc, Color color) {
        ParticleBuilder.playColored(loc, color, Particle.DUST);
    }

    public static void playColoredSpell(Location loc, Color color) {
        ParticleBuilder.playColored(loc, color, Particle.ENTITY_EFFECT);
    }
}

