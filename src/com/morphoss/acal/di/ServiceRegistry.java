/*
 * Copyright (C) 2011 Morphoss Ltd
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.morphoss.acal.di;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;

import android.util.Log;

/**
 * Simple service locator pattern for dependency injection.
 * Provides a central registry for singleton manager instances,
 * allowing decoupled access to services throughout the application.
 *
 * Usage:
 * - Register services: ServiceRegistry.register(IResourceManager.class, resourceManager)
 * - Retrieve services: ServiceRegistry.get(IResourceManager.class)
 * - Cleanup: ServiceRegistry.close()
 */
public final class ServiceRegistry {

    private static final String TAG = "ServiceRegistry";

    private static final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();

    private ServiceRegistry() {
        // Prevent instantiation
    }

    /**
     * Register a service instance with the registry.
     *
     * @param serviceClass The interface class used as the key
     * @param instance The implementation instance
     * @param <T> The service type
     * @throws IllegalArgumentException if serviceClass or instance is null
     * @throws IllegalStateException if a service of this type is already registered
     */
    public static <T> void register(Class<T> serviceClass, T instance) {
        if (serviceClass == null) {
            throw new IllegalArgumentException("Service class cannot be null");
        }
        if (instance == null) {
            throw new IllegalArgumentException("Service instance cannot be null");
        }

        Object existing = services.putIfAbsent(serviceClass, instance);
        if (existing != null && existing != instance) {
            Log.w(TAG, "Service already registered for " + serviceClass.getSimpleName() +
                       ", keeping existing instance");
        }
    }

    /**
     * Retrieve a registered service instance.
     *
     * @param serviceClass The interface class used as the key
     * @param <T> The service type
     * @return The registered instance, or null if not registered
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> serviceClass) {
        if (serviceClass == null) {
            return null;
        }
        return (T) services.get(serviceClass);
    }

    /**
     * Check if a service is registered.
     *
     * @param serviceClass The interface class to check
     * @return true if registered, false otherwise
     */
    public static boolean isRegistered(Class<?> serviceClass) {
        return serviceClass != null && services.containsKey(serviceClass);
    }

    /**
     * Unregister a service from the registry.
     *
     * @param serviceClass The interface class to unregister
     * @param <T> The service type
     * @return The previously registered instance, or null if none was registered
     */
    @SuppressWarnings("unchecked")
    public static <T> T unregister(Class<T> serviceClass) {
        if (serviceClass == null) {
            return null;
        }
        return (T) services.remove(serviceClass);
    }

    /**
     * Close all registered services that implement Closeable and clear the registry.
     * Should be called during application shutdown.
     */
    public static void close() {
        for (Object service : services.values()) {
            if (service instanceof Closeable) {
                try {
                    ((Closeable) service).close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing service: " + service.getClass().getSimpleName(), e);
                }
            }
        }
        services.clear();
    }

    /**
     * Clear all registered services without closing them.
     * Use this for testing or when services are managed externally.
     */
    public static void clear() {
        services.clear();
    }

    /**
     * Get the number of registered services.
     * Primarily useful for testing.
     *
     * @return The count of registered services
     */
    public static int size() {
        return services.size();
    }
}
