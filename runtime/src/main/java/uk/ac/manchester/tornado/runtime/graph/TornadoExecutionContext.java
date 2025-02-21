/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graph;

import static uk.ac.manchester.tornado.runtime.common.Tornado.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.common.SchedulableTask;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoAcceleratorDevice;
import uk.ac.manchester.tornado.runtime.tasks.LocalObjectState;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class TornadoExecutionContext {

    private final String name;
    private final int MAX_TASKS = 128;
    private final ScheduleMetaData meta;

    private final List<SchedulableTask> tasks;
    private final List<Object> constants;
    private final Map<Integer, Integer> objectMap;
    private final List<Object> objects;
    private final List<LocalObjectState> objectState;
    private final List<TornadoAcceleratorDevice> devices;
    private CallStack[] stacks;
    private final int[] taskToDevice;
    private int nextTask;

    private HashSet<TornadoAcceleratorDevice> lastDevices;

    private boolean redeployOnDevice;

    public TornadoExecutionContext(String id) {
        name = id;
        meta = new ScheduleMetaData(name);
        tasks = new ArrayList<>();
        constants = new ArrayList<>();
        objectMap = new HashMap<>();
        objects = new ArrayList<>();
        objectState = new ArrayList<>();
        devices = new ArrayList<>();
        stacks = new CallStack[MAX_TASKS];
        taskToDevice = new int[MAX_TASKS];
        Arrays.fill(taskToDevice, -1);
        nextTask = 0;
        lastDevices = new HashSet<>();
    }

    public CallStack[] getFrames() {
        return stacks;
    }

    public int insertVariable(Object var) {
        int index = -1;
        if (var.getClass().isPrimitive() || RuntimeUtilities.isBoxedPrimitiveClass(var.getClass())) {
            index = constants.indexOf(var);
            if (index == -1) {
                index = constants.size();
                constants.add(var);
            }
        } else if (objectMap.containsKey(var.hashCode())) {
            index = objectMap.get(var.hashCode());
        } else {
            index = objects.size();
            objects.add(var);
            objectMap.put(var.hashCode(), index);
            objectState.add(index, new LocalObjectState(var));
        }
        return index;
    }

    public int getTaskCount() {
        return nextTask;
    }

    public void incrGlobalTaskCount() {
        nextTask++;
    }

    public int hasTask(SchedulableTask task) {
        return tasks.indexOf(task);
    }

    public int addTask(SchedulableTask task) {
        int index = tasks.indexOf(task);
        if (index == -1) {
            index = tasks.size();
            tasks.add(task);
        }
        return index;
    }

    public List<Object> getConstants() {
        return constants;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public int getDeviceIndexForTask(int index) {
        return taskToDevice[index];
    }

    public TornadoAcceleratorDevice getDeviceForTask(int index) {
        return getDevice(taskToDevice[index]);
    }

    public TornadoAcceleratorDevice getDevice(int index) {
        return devices.get(index);
    }

    public SchedulableTask getTask(int index) {
        return tasks.get(index);
    }

    public void apply(Consumer<SchedulableTask> consumer) {
        for (SchedulableTask task : tasks) {
            consumer.accept(task);
        }
    }

    public void mapAllTo(TornadoDevice mapping) {

        if (mapping instanceof TornadoAcceleratorDevice) {
            devices.clear();
            devices.add(0, (TornadoAcceleratorDevice) mapping);
            apply(task -> task.mapTo(mapping));
            Arrays.fill(taskToDevice, 0);
        } else {
            throw new RuntimeException("Device " + mapping.getClass() + " not supported yet");
        }
    }

    public void addDevice(int deviceId) {
        devices.add((TornadoAcceleratorDevice) TornadoCoreRuntime.getTornadoRuntime().getDriver(0).getDevice(deviceId));
    }

    public void addDevice(TornadoAcceleratorDevice device) {
        devices.add(device);
    }

    public void setDevice(int index, TornadoAcceleratorDevice device) {
        devices.set(index, device);
    }

    private void assignTask(int index, SchedulableTask task) {
        if (taskToDevice[index] != -1) {
            return;
        }

        String id = task.getId();
        TornadoDevice target = task.getDevice();
        TornadoAcceleratorDevice accelerator = null;

        if (target instanceof TornadoAcceleratorDevice) {
            accelerator = (TornadoAcceleratorDevice) target;
        } else {
            throw new TornadoRuntimeException("Device " + target.getClass() + " not supported yet");
        }

        info("assigning %s to %s", id, target.getDeviceName());

        int deviceIndex = devices.indexOf(target);
        if (deviceIndex == -1) {
            deviceIndex = devices.size();
            devices.add(accelerator);
        }
        taskToDevice[index] = deviceIndex;
    }

    public void assignToDevices() {
        for (int i = 0; i < tasks.size(); i++) {
            assignTask(i, tasks.get(i));
        }
    }

    public TornadoDevice getDeviceFirtTask() {
        return tasks.get(0).getDevice();
    }

    public LocalObjectState getObjectState(Object object) {
        return objectState.get(insertVariable(object));
    }

    public void print() {
        System.out.println("device table:");
        for (int i = 0; i < devices.size(); i++) {
            System.out.printf("[%d]: %s\n", i, devices.get(i));
        }

        System.out.println("constant table:");
        for (int i = 0; i < constants.size(); i++) {
            System.out.printf("[%d]: %s\n", i, constants.get(i));
        }

        System.out.println("object table:");
        for (int i = 0; i < objects.size(); i++) {
            final Object obj = objects.get(i);
            System.out.printf("[%d]: 0x%x %s\n", i, obj.hashCode(), obj.toString());
        }

        System.out.println("task table:");
        for (int i = 0; i < tasks.size(); i++) {
            final SchedulableTask task = tasks.get(i);
            System.out.printf("[%d]: %s\n", i, task.getFullName());
        }
    }

    public List<LocalObjectState> getObjectStates() {
        return objectState;
    }

    public List<SchedulableTask> getTasks() {
        return tasks;
    }

    public List<TornadoAcceleratorDevice> getDevices() {
        return devices;
    }

    /**
     * Default device inspects the driver 0 and device 0 of the internal OpenCL
     * list.
     * 
     * @return {@link TornadoAcceleratorDevice}
     */
    public TornadoAcceleratorDevice getDefaultDevice() {
        return meta.getDevice();
    }

    public SchedulableTask getTask(String id) {
        for (int i = 0; i < tasks.size(); i++) {
            final String canonicalisedId;
            if (id.startsWith(getId())) {
                canonicalisedId = id;
            } else {
                canonicalisedId = getId() + "." + id;
            }
            if (tasks.get(i).getId().equalsIgnoreCase(canonicalisedId)) {
                return tasks.get(i);
            }
        }
        return null;
    }

    public CallStack getFrame(String id) {
        for (int i = 0; i < tasks.size(); i++) {
            final String canonicalisedId;
            if (id.startsWith(getId())) {
                canonicalisedId = id;
            } else {
                canonicalisedId = getId() + "." + id;
            }
            if (tasks.get(i).getId().equalsIgnoreCase(canonicalisedId)) {
                return stacks[i];
            }
        }
        return null;
    }

    public TornadoAcceleratorDevice getDeviceForTask(String id) {
        TornadoDevice device = getTask(id).getDevice();
        TornadoAcceleratorDevice tornadoDevice = null;
        if (device instanceof TornadoAcceleratorDevice) {
            tornadoDevice = (TornadoAcceleratorDevice) device;
        } else {
            throw new RuntimeException("Device " + device.getClass() + " not supported yet");
        }
        return getTask(id) == null ? null : tornadoDevice;
    }

    public String getId() {
        return name;
    }

    public ScheduleMetaData meta() {
        return meta;
    }

    public void sync() {
        for (int i = 0; i < objects.size(); i++) {
            Object object = objects.get(i);
            if (object != null) {
                final LocalObjectState localState = objectState.get(i);
                localState.sync(object);
            }
        }
    }

    public void addLastDevice(TornadoAcceleratorDevice device) {
        lastDevices.add(device);
    }

    public HashSet<TornadoAcceleratorDevice> getLastDevices() {
        return lastDevices;
    }

    public void newStack(boolean newStack) {
        this.redeployOnDevice = newStack;
    }

    public boolean redeployOnDevice() {
        return this.redeployOnDevice;
    }
}
