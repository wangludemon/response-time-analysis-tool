package com.demo.tool.responsetimeanalysis.generator;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;

public class AllocationGeneator {

    public static Logger log = LogManager.getLogger();

    public ArrayList<ArrayList<SporadicTask>> allocateTasks(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int total_partitions,
                                                            int policy) {
        log.debug("Task allocation started");
        int Osize = tasksToAllocate.size();
        double totalUtil = 0.0;
        for (SporadicTask sporadicTask : tasksToAllocate) {
            totalUtil += sporadicTask.util;
        }
        //double totalUtil = 0.05 * tasksToAllocate.size();

        double maxUtilPerCore;
        if (totalUtil / total_partitions < 0.5)
            maxUtilPerCore = 0.5;
        else if (totalUtil / total_partitions < 0.6)
            maxUtilPerCore = 0.6;
        else if (totalUtil / total_partitions < 0.65)
            maxUtilPerCore = 0.65;
        else
            maxUtilPerCore = totalUtil / total_partitions <= 0.9 ? (totalUtil / total_partitions) + 0.05 : 1;


        ArrayList<ArrayList<SporadicTask>> tasks = switch (policy) {
            case 0 -> WF(tasksToAllocate, total_partitions);
            case 1 -> BF(tasksToAllocate, total_partitions, maxUtilPerCore);
            case 2 -> FF(tasksToAllocate, total_partitions, maxUtilPerCore);
            case 3 -> NF(tasksToAllocate, total_partitions, maxUtilPerCore);
            default -> null;
        };



        if (tasks != null) {
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).size() == 0) {
                    tasks.remove(i);
                    i--;
                }
            }

            for (int i = 0; i < tasks.size(); i++) {
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    tasks.get(i).get(j).partition = i;
                }
            }

            if (resources != null && resources.size() > 0) {
                for (Resource res : resources) {
                    res.isGlobal = false;
                    res.partitions.clear();
                    res.requested_tasks.clear();
                }

                /* for each resource */
                for (Resource resource : resources) {
                    /* for each partition */
                    for (ArrayList<SporadicTask> sporadicTasks : tasks) {

                        /* for each task in the given partition */
                        for (SporadicTask task : sporadicTasks) {
                            if (task.resource_required_index.contains(resource.id - 1)) {
                                resource.requested_tasks.add(task);
                                if (!resource.partitions.contains(task.partition)) {
                                    resource.partitions.add(task.partition);
                                }
                            }
                        }
                    }

                    if (resource.partitions.size() > 1)
                        resource.isGlobal = true;
                }
            }

        }

        if (tasks != null) {
            int Nsize = 0;
            for (ArrayList<SporadicTask> task : tasks) {
                Nsize += task.size();
            }

            if (Osize != Nsize) {
                System.err.println("Allocation error!");
            }
        }
        log.debug("Task allocation completed");
        return tasks;
    }

    private ArrayList<ArrayList<SporadicTask>> WF(ArrayList<SporadicTask> tasksToAllocate, int partitions) {

        // clear tasks' partitions
        for (SporadicTask sporadicTask : tasksToAllocate) {
            sporadicTask.partition = -1;
        }

        tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

        // Init allocated tasks array
        ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            ArrayList<SporadicTask> task = new ArrayList<>();
            tasks.add(task);
        }

        // init util array
        ArrayList<Double> utilPerPartition = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            utilPerPartition.add((double) 0);
        }

        for (SporadicTask task : tasksToAllocate) {
            int target = -1;
            double minUtil = 2;
            for (int j = 0; j < partitions; j++) {
                if (minUtil > utilPerPartition.get(j)) {
                    minUtil = utilPerPartition.get(j);
                    target = j;
                }
            }

            if (target == -1) {
                System.err.println("WF error!");
                return null;
            }

            if ((double) 1 - minUtil >= task.util) {
                task.partition = target;
                utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
            } else
                return null;
        }

        for (SporadicTask sporadicTask : tasksToAllocate) {
            int partition = sporadicTask.partition;
            tasks.get(partition).add(sporadicTask);
        }

        for (ArrayList<SporadicTask> task : tasks) {
            task.sort(Comparator.comparingDouble(p -> p.period));
        }

        return tasks;
    }

    private ArrayList<ArrayList<SporadicTask>> BF(ArrayList<SporadicTask> tasksToAllocate, int partitions, double maxUtilPerCore) {
        for (SporadicTask sporadicTask : tasksToAllocate) {
            sporadicTask.partition = -1;
        }
        tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

        ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            ArrayList<SporadicTask> task = new ArrayList<>();
            tasks.add(task);
        }

        ArrayList<Double> utilPerPartition = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            utilPerPartition.add((double) 0);
        }

        for (SporadicTask task : tasksToAllocate) {
            int target = -1;
            double maxUtil = -1;
            for (int j = 0; j < partitions; j++) {
                if (maxUtil < utilPerPartition.get(j) && ((maxUtilPerCore - utilPerPartition.get(j) >= task.util)
                        || (task.util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= task.util))) {
                    maxUtil = utilPerPartition.get(j);
                    target = j;
                }
            }

            if (target < 0) {
                return null;
            } else {
                task.partition = target;
                utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
            }
        }

        for (SporadicTask sporadicTask : tasksToAllocate) {
            int partition = sporadicTask.partition;
            tasks.get(partition).add(sporadicTask);
        }

        for (ArrayList<SporadicTask> task : tasks) {
            task.sort(Comparator.comparingDouble(p -> p.period));
        }

        return tasks;
    }

    private ArrayList<ArrayList<SporadicTask>> FF(ArrayList<SporadicTask> tasksToAllocate, int partitions, double maxUtilPerCore) {
        for (int i = 0; i < tasksToAllocate.size(); i++) {
            tasksToAllocate.get(i).partition = -1;
        }
        tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

        ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            ArrayList<SporadicTask> task = new ArrayList<>();
            tasks.add(task);
        }

        ArrayList<Double> utilPerPartition = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            utilPerPartition.add((double) 0);
        }

        for (int i = 0; i < tasksToAllocate.size(); i++) {
            SporadicTask task = tasksToAllocate.get(i);
            for (int j = 0; j < partitions; j++) {
                if ((maxUtilPerCore - utilPerPartition.get(j) >= task.util) || (task.util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= task.util)) {
                    task.partition = j;
                    utilPerPartition.set(j, utilPerPartition.get(j) + task.util);
                    break;
                }
            }
            if (task.partition == -1)
                return null;
        }

        for (int i = 0; i < tasksToAllocate.size(); i++) {
            int partition = tasksToAllocate.get(i).partition;
            tasks.get(partition).add(tasksToAllocate.get(i));
        }

        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
        }

        return tasks;
    }

    private ArrayList<ArrayList<SporadicTask>> NF(ArrayList<SporadicTask> tasksToAllocate, int partitions, double maxUtilPerCore) {
        for (int i = 0; i < tasksToAllocate.size(); i++) {
            tasksToAllocate.get(i).partition = -1;
        }
        tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

        ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            ArrayList<SporadicTask> task = new ArrayList<>();
            tasks.add(task);
        }

        ArrayList<Double> utilPerPartition = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            utilPerPartition.add((double) 0);
        }

        int currentIndex = 0;

        for (int i = 0; i < tasksToAllocate.size(); i++) {
            SporadicTask task = tasksToAllocate.get(i);

            for (int j = 0; j < partitions; j++) {
                if ((maxUtilPerCore - utilPerPartition.get(currentIndex) >= task.util)
                        || (task.util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= task.util)) {
                    task.partition = currentIndex;
                    utilPerPartition.set(currentIndex, utilPerPartition.get(currentIndex) + task.util);
                    break;
                }
                if (currentIndex == partitions - 1)
                    currentIndex = 0;
                else
                    currentIndex++;
            }
            if (task.partition == -1)
                return null;
        }

        for (int i = 0; i < tasksToAllocate.size(); i++) {
            int partition = tasksToAllocate.get(i).partition;
            tasks.get(partition).add(tasksToAllocate.get(i));
        }

        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
        }

        return tasks;
    }




}
