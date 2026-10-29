package cn.mythicland.framework.bukkit.store;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

public final class StoredPlayerSession<P, I> {
    private final UUID uniqueId;
    private final StoredPlayerProfile<P> profileVersioner;
    private final ReentrantLock flushLock = new ReentrantLock(true);

    private P profileData;
    private I inventoryData;
    private long inventoryVersion;
    private boolean profileExists;
    private long profileRevision;
    private long inventoryRevision;
    private boolean profileDirty;
    private boolean inventoryDirty;
    private boolean profileFlushInProgress;
    private boolean inventoryFlushInProgress;

    public StoredPlayerSession(StoredPlayerData<P, I> playerState, StoredPlayerProfile<P> profileVersioner) {
        this.uniqueId = playerState.uniqueId();
        this.profileData = playerState.profileData();
        this.inventoryData = playerState.inventoryData();
        this.inventoryVersion = playerState.inventoryVersion();
        this.profileExists = playerState.profileExists();
        this.profileVersioner = profileVersioner;
    }

    public UUID uniqueId() {
        return this.uniqueId;
    }

    ReentrantLock flushLock() {
        return this.flushLock;
    }

    public synchronized StoredPlayerData<P, I> snapshot() {
        return new StoredPlayerData<>(
                this.uniqueId,
                this.profileData,
                this.inventoryData,
                this.inventoryVersion,
                this.profileExists
        );
    }

    public synchronized P profileData() {
        return this.profileData;
    }

    public synchronized boolean profileExists() {
        return this.profileExists;
    }

    public synchronized long profileRevision() {
        return this.profileRevision;
    }

    public synchronized long inventoryRevision() {
        return this.inventoryRevision;
    }

    public synchronized void markProfileExists() {
        this.profileExists = true;
    }

    public synchronized void captureInventory(I inventoryData) {
        if (Objects.equals(this.inventoryData, inventoryData)) return;
        this.inventoryData = inventoryData;
        this.inventoryDirty = true;
        this.inventoryRevision++;
        if (this.profileVersioner.version(this.profileData) == 0L) {
            this.profileDirty = true;
            this.profileRevision++;
        }
    }

    public synchronized P updateProfile(UnaryOperator<P> updater) {
        P updatedProfile = updater.apply(this.profileData);
        if (updatedProfile == null) throw new IllegalStateException("Updated profile cannot be null");
        this.profileData = updatedProfile;
        this.profileDirty = true;
        this.profileRevision++;
        return this.profileData;
    }

    public synchronized StoredPlayerFlush<P, I> prepareFlush() {
        boolean flushProfile = this.profileDirty && !this.profileFlushInProgress;
        boolean flushInventory = this.inventoryDirty && !this.inventoryFlushInProgress;
        if (!flushProfile && !flushInventory) return null;
        if (flushProfile) {
            this.profileFlushInProgress = true;
        }
        if (flushInventory) {
            this.inventoryFlushInProgress = true;
        }
        return new StoredPlayerFlush<>(
                this.uniqueId,
                this.profileData,
                this.inventoryData,
                this.profileVersioner.version(this.profileData),
                this.inventoryVersion,
                this.profileRevision,
                this.inventoryRevision,
                flushProfile,
                flushInventory
        );
    }

    public synchronized void flushSucceeded(StoredPlayerFlush<P, I> request) {
        if (request.flushProfile()) {
            this.profileExists = true;
            this.profileData = this.profileVersioner.nextVersion(this.profileData);
            this.profileFlushInProgress = false;
            if (this.profileRevision == request.profileRevision()) {
                this.profileDirty = false;
            }
        }
        if (request.flushInventory()) {
            this.inventoryVersion++;
            this.inventoryFlushInProgress = false;
            if (this.inventoryRevision == request.inventoryRevision()) {
                this.inventoryDirty = false;
            }
        }
    }

    public synchronized void flushFailed(StoredPlayerFlush<P, I> request) {
        if (request.flushProfile()) {
            this.profileFlushInProgress = false;
        }
        if (request.flushInventory()) {
            this.inventoryFlushInProgress = false;
        }
    }

    public synchronized boolean hasPendingChanges() {
        return this.profileDirty || this.inventoryDirty;
    }
}
