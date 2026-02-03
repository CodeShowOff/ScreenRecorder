/*
 * Copyright (c) 2016-2017. Vijai Chandra Prasad R.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses
 */

package com.orpheusdroid.screenrecorder.folderpicker;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Model class representing a storage location (internal or external).
 */
public class Storages {

    @NonNull
    private final String path;
    @NonNull
    private final StorageType type;

    public Storages(@NonNull String path, @NonNull StorageType type) {
        this.path = Objects.requireNonNull(path, "Path cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
    }

    @NonNull
    public String getPath() {
        return path;
    }

    @NonNull
    public StorageType getType() {
        return type;
    }

    @NonNull
    @Override
    public String toString() {
        return "Storages{path='" + path + "', type=" + type + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Storages storages = (Storages) o;
        return path.equals(storages.path) && type == storages.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, type);
    }

    /**
     * Enum representing the type of storage.
     */
    public enum StorageType {
        Internal,
        External
    }
}
