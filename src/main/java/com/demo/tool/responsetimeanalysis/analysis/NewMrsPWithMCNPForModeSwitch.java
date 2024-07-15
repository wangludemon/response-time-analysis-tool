//package com.demo.tool.responsetimeanalysis.analysis;
//
//import com.demo.tool.responsetimeanalysis.entity.Resource;
//import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
//import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
//
//import java.util.ArrayList;
//
//public class NewMrsPWithMCNPForModeSwitch {
//    long count = 0;
//
//    public long[][] NewMrsPRTATest(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long mig, long np,
//                                   boolean printDebug) {
//        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);
//
//        long[][] response_time = new long[tasks.size()][];
//        boolean isEqual = false, missDeadline = false;
//        count = 0;
//
//        for (int i = 0; i < init_Ri.length; i++) {
//            response_time[i] = new long[init_Ri[i].length];
//        }
//
//        new AnalysisUtils().cloneList(init_Ri, response_time);
//
//        /* a huge busy window to get a fixed Ri */
//        while (!isEqual) {
//            isEqual = true;
//            long[][] response_time_plus = busyWindow(tasks, resources, response_time, mig, np);
//
//            for (int i = 0; i < response_time_plus.length; i++) {
//                for (int j = 0; j < response_time_plus[i].length; j++) {
//                    if (response_time[i][j] != response_time_plus[i][j])
//                        isEqual = false;
//
//                    if (response_time_plus[i][j] > tasks.get(i).get(j).deadline)
//                        missDeadline = true;
//                }
//            }
//
//            count++;
//            new AnalysisUtils().cloneList(response_time_plus, response_time);
//
//            if (missDeadline)
//                break;
//        }
//
//        if (printDebug) {
//            if (missDeadline)
//                System.out.println("NewMrsPRTAWithMigration    after " + count + " tims of recursion, the tasks miss the deadline.");
//            else
//                System.out.println("NewMrsPRTAWithMigration    after " + count + " tims of recursion, we got the response time.");
//            new AnalysisUtils().printResponseTime(response_time, tasks);
//        }
//
//        return response_time;
//    }
//
//    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time,
//                                long oneMig, long np) {
//        long[][] response_time_plus = new long[tasks.size()][];
//
//        for (int i = 0; i < response_time.length; i++) {
//            response_time_plus[i] = new long[response_time[i].length];
//        }
//
//        for (int i = 0; i < tasks.size(); i++) {
//            for (int j = 0; j < tasks.get(i).size(); j++) {
//                SporadicTask task = tasks.get(i).get(j);
//                task.spin = resourceAccessingTime(task, tasks, resources, response_time, response_time[i][j], 0, oneMig, np);
//                task.interference = highPriorityInterference(task, tasks, response_time[i][j], response_time, resources, oneMig, np);
//
//                task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j], oneMig, np);
//                long npsection = (isTaskIncurNPSection(task, tasks.get(task.partition), resources, tasks) ? np : 0);
//                task.local = Long.max(task.local, npsection);
//
//                response_time_plus[i][j] = task.Ri = task.WCET + task.spin + task.interference + task.local;
//
//                if (task.Ri > task.deadline)
//                    return response_time_plus;
//
//            }
//        }
//        return response_time_plus;
//    }
//
//    private boolean isTaskIncurNPSection(SporadicTask task, ArrayList<SporadicTask> tasksOnItsParititon, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> tasks) {
//        int partition = task.partition;
//        int priority = task.priority;
//        int minCeiling = 1000;
//
//        for (int i = 0; i < resources.size(); i++) {
//            Resource resource = resources.get(i);
//            //if (resource.partitions.contains(partition) && minCeiling > resource.ceiling.get(resource.partitions.indexOf(partition))) {
//            if (resource.partitions.contains(partition) && minCeiling > resource.getCeilingForProcessor(tasks.get(partition))) {
//                //minCeiling = resource.ceiling.get(resource.partitions.indexOf(partition));
//                minCeiling = resource.getCeilingForProcessor(tasks.get(partition));
//            }
//        }
//
//        if (priority > minCeiling)
//            return true;
//        else
//            return false;
//    }
//
//    /*
//     * Calculate the local blocking for task t.
//     */
//    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris,
//                               long time, long oneMig, long np) {
//        ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, tasks);
//        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();
//
//        for (int i = 0; i < LocalBlockingResources.size(); i++) {
//            ArrayList<Integer> migration_targets = new ArrayList<>();
//
//            Resource res = LocalBlockingResources.get(i);
//            long local_blocking = res.csl;
//
//            migration_targets.add(t.partition);
//
//            if (res.isGlobal) {
//                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
//                    int partition = res.partitions.get(parition_index);
//                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], time);
//                    int norT = t.resource_required_index.contains(res.id - 1)
//                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1)) : 0;
//                    int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], time);
//
//                    if (partition != t.partition && (norHP + norT) < norR) {
//                        local_blocking += res.csl;
//                        migration_targets.add(partition);
//                    }
//                }
//
//                if (oneMig != 0) {
//                    local_blocking += migrationCostForArrival(oneMig, np, migration_targets, res, tasks);
//                }
//            }
//
//            local_blocking_each_resource.add(local_blocking);
//        }
//
//        if (local_blocking_each_resource.size() > 1)
//            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));
//
//        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
//
//    }
//
//    /*
//     * Calculate the local high priority tasks' interference for a given task t.
//     * CI is a set of computation time of local tasks, including spin delay.
//     */
//    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long time, long[][] Ris,
//                                          ArrayList<Resource> resources, long oneMig, long np) {
//        long interference = 0;
//        int partition = t.partition;
//        ArrayList<SporadicTask> tasks = allTasks.get(partition);
//
//        for (int i = 0; i < tasks.size(); i++) {
//            if (tasks.get(i).priority > t.priority) {
//                SporadicTask hpTask = tasks.get(i);
//                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
//                interference += resourceAccessingTime(hpTask, allTasks, resources, Ris, time, 0, oneMig, np);
//            }
//        }
//        return interference;
//    }
//
//    private long resourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources,
//                                       long[][] Ris, long time, long jitter, long oneMig, long np) {
//        long resource_accessing_time = 0;
//
//        // 对任务访问的每个资源
//        for (int i = 0; i < task.resource_required_index.size(); i++) {
//            Resource resource = resources.get(task.resource_required_index.get(i));
//
//            int number_of_request_with_btb = (int) Math.ceil((double) (time + jitter) / (double) task.period)
//                    * task.number_of_access_in_one_release.get(i);
//            // 每次访问
//            for (int j = 1; j < number_of_request_with_btb + 1; j++) {
//                long oneAccess = 0;
//                oneAccess += resourceAccessingTimeInOne(task, resource, tasks, Ris, time, jitter, j);
//
//                if (oneMig != 0)
//                    oneAccess += migrationCostForSpin(oneMig, np, task, j, resource, tasks, time, Ris);
//
//                resource_accessing_time += oneAccess;
//            }
//        }
//
//        return resource_accessing_time;
//    }
//
//    private long resourceAccessingTimeInOne(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> lowTasks,
//                                            long[][] Ris,
//                                            long time, long jitter, int n) {
//        int number_of_access = 0;
//        int spin = 0;
//
//        for (int i = 0; i < tasks.size(); i++) {
//            if (i != task.partition) {
//                /* For each remote partition */
//                int number_of_request_by_Remote_P = 0;
//                // 计算远程访问次数 高关键任务
//                for (int j = 0; j < tasks.get(i).size(); j++) {
//                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
//                        SporadicTask remote_task = tasks.get(i).get(j);
//                        int indexR = getIndexRInTask(remote_task, resource);
//                        int number_of_release = (int) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
//                        number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
//                    }
//                }
//                int number_of_request_by_Remote_P_from_low = 0;
//
//                // 计算远程访问次数 低关键任务
//                for (int j = 0; j < lowTasks.get(i).size(); j++) {
//                    if (lowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
//                        SporadicTask remote_task = lowTasks.get(i).get(j);
//                        int indexR = getIndexRInTask(remote_task, resource);
//                        int number_of_release = (int) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
//                        number_of_request_by_Remote_P_from_low += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
//                    }
//                }
//
//                int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), lowTasks.get(task.partition), Ris[task.partition], time);
//
//                // 剩余次数
//                int possible_spin_delay = Math.max(number_of_request_by_Remote_P - getNoRFromHP - n + 1, 0);
//
//                if (possible_spin_delay > 0)
//                    spin += resource.csl_high;
//                else {
//                    possible_spin_delay = Math.max(number_of_request_by_Remote_P + number_of_request_by_Remote_P_from_low - getNoRFromHP - n + 1, 0);
//                    spin += (int) (Integer.min(possible_spin_delay, 1) * resource.csl_low);
//                }
//
//                //number_of_access += Integer.min(possible_spin_delay, 1);
//            }
//        }
//
//        // account for the request of the task itself
//        number_of_access++;
//
//        return number_of_access * resource.csl;
//    }
//
//    private long migrationCostForArrival(long oneMig, long np, ArrayList<Integer> migration_targets, Resource resource,
//                                         ArrayList<ArrayList<SporadicTask>> tasks) {
//        return migrationCost(oneMig, np, migration_targets, resource, tasks);
//    }
//
//    private long migrationCostForSpin(long oneMig, long np, SporadicTask task, int request_number, Resource resource,
//                                      ArrayList<ArrayList<SporadicTask>> tasks, long time, long[][] Ris) {
//
//        ArrayList<Integer> migration_targets = new ArrayList<>();
//
//        // identify the migration targets
//        migration_targets.add(task.partition);
//        for (int i = 0; i < tasks.size(); i++) {
//            if (i != task.partition) {
//                int number_requests_left = 0;
//                number_requests_left = getNoRRemote(resource, tasks.get(i), Ris[i], time)
//                        - getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time) - request_number + 1;
//
//                if (number_requests_left > 0)
//                    migration_targets.add(i);
//            }
//        }
//
//        return migrationCost(oneMig, np, migration_targets, resource, tasks);
//    }
//
//    private long migrationCost(long oneMig, long np, ArrayList<Integer> migration_targets, Resource resource,
//                               ArrayList<ArrayList<SporadicTask>> tasks) {
//        long migrationCost = 0;
//        ArrayList<Integer> migration_targets_with_P = new ArrayList<>();
//
//        // identify the migration targets with preemptors
//        for (int i = 0; i < migration_targets.size(); i++) {
//            int partition = migration_targets.get(i);
//            //if (tasks.get(partition).get(0).priority > resource.ceiling.get(resource.partitions.indexOf(partition)))
//
//            if (tasks.get(partition).get(0).priority > resource.getCeilingForProcessor(tasks.get(partition)))
//                migration_targets_with_P.add(migration_targets.get(i));
//        }
//
//        // check
//        if (!migration_targets.containsAll(migration_targets_with_P)) {
//            System.out.println("migration targets error!");
//            System.exit(0);
//        }
//
//        // now we compute the migration cost for each request
//        for (int i = 0; i < migration_targets.size(); i++) {
//            long migration_cost_for_one_access = 0;
//            int partition = migration_targets.get(i); // the request issued
//            // from.
//
//            // calculating migration cost
//            // 1. If there is no preemptors on the task's partition OR there is
//            // no
//            // other migration targets
//            if (!migration_targets_with_P.contains(partition) || (migration_targets.size() == 1 && migration_targets.get(0) == partition))
//                migration_cost_for_one_access = 0;
//
//                // 2. If there is preemptors on the task's partition AND there are
//                // no
//                // preemptors on other migration targets
//            else if (migration_targets_with_P.size() == 1 && migration_targets_with_P.get(0) == partition && migration_targets.size() > 1)
//                migration_cost_for_one_access = 2 * oneMig;
//
//                // 3. If there exist multiple migration targets with preemptors.
//                // With NP
//                // section applied.
//            else {
//                long migCostWithHP = migrationCostBusyWindow(migration_targets_with_P, oneMig, np, resource, tasks);
//                long migCostWithNP = (long) (1 + Math.ceil((double) resource.csl / (double) np)) * oneMig;
//
//                migration_cost_for_one_access = Math.min(migCostWithHP, migCostWithNP);
//            }
//
//            migrationCost += migration_cost_for_one_access;
//        }
//
//        return migrationCost;
//    }
//
//    public long migrationCostBusyWindow(ArrayList<Integer> migration_targets_with_P, long oneMig, long np, Resource resource,
//                                        ArrayList<ArrayList<SporadicTask>> tasks) {
//        long migCost = 0;
//
//        long migCostWithNP = (long) (1 + Math.ceil((double) resource.csl / (double) np)) * oneMig;
//        long newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, resource.csl + migCost, resource, tasks);
//
//        while (migCost != newMigCost) {
//            migCost = newMigCost;
//            newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, resource.csl + migCost, resource, tasks);
//
//            if (newMigCost >= migCostWithNP) {
//                return newMigCost;
//            }
//        }
//
//        return migCost;
//    }
//
//    public long migrationCostOneCal(ArrayList<Integer> migration_targets_with_P, long oneMig, long duration, Resource resource,
//                                    ArrayList<ArrayList<SporadicTask>> tasks) {
//        long migCost = 0;
//
//        for (int i = 0; i < migration_targets_with_P.size(); i++) {
//            int partition_with_p = migration_targets_with_P.get(i);
//            int ceiling_index = resource.partitions.indexOf(partition_with_p);
//
//            for (int j = 0; j < tasks.get(partition_with_p).size(); j++) {
//                SporadicTask hpTask = tasks.get(partition_with_p).get(j);
//
//                //if (hpTask.priority > resource.ceiling.get(ceiling_index))
//                if (hpTask.priority > resource.getCeilingForProcessor(tasks.get(ceiling_index)))
//                    migCost += Math.ceil((double) (duration) / (double) hpTask.period) * oneMig;
//            }
//        }
//
//        return migCost + oneMig;
//    }
//
//    /*
//     * gives a set of resources that can cause local blocking for a given task
//     */
//    private ArrayList<Resource> getLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> tasks) {
//        ArrayList<Resource> localBlockingResources = new ArrayList<>();
//        int partition = task.partition;
//
//        for (int i = 0; i < resources.size(); i++) {
//            Resource resource = resources.get(i);
//
//            //if (resource.partitions.contains(partition) && resource.ceiling.get(resource.partitions.indexOf(partition)) >= task.priority) {
//            if (resource.partitions.contains(partition) && resource.getCeilingForProcessor(tasks.get(partition)) >= task.priority) {
//                for (int j = 0; j < resource.requested_tasks.size(); j++) {
//                    SporadicTask LP_task = resource.requested_tasks.get(j);
//                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
//                        localBlockingResources.add(resource);
//                        break;
//                    }
//                }
//            }
//        }
//
//        return localBlockingResources;
//    }
//
//    /*
//     * gives that number of requests from HP local tasks for a resource that is
//     * required by the given task.
//     */
//    private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, ArrayList<SporadicTask> lowTasks, long[] Ris, long Ri) {
//        int number_of_request_by_HP = 0;
//        int priority = task.priority;
//
//        for (int i = 0; i < tasks.size(); i++) {
//            if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
//                SporadicTask hpTask = tasks.get(i);
//                int indexR = getIndexRInTask(hpTask, resource);
//                number_of_request_by_HP += (int) (Math.ceil((double) (Ri) / (double) hpTask.period)
//                                        * hpTask.number_of_access_in_one_release.get(indexR));
//            }
//        }
//        // 计算低关键任务的访问次数，累加到number_of_request_by_HP
//        for (int i = 0; i < lowTasks.size(); i++) {
//            if (lowTasks.get(i).priority > priority && lowTasks.get(i).resource_required_index.contains(resource.id - 1)) {
//                SporadicTask hpTask = lowTasks.get(i);
//                int indexR = getIndexRInTask(hpTask, resource);
//                number_of_request_by_HP += (int) (Math.ceil((double) (task.Ri_LO) / (double) hpTask.period)
//                        * hpTask.number_of_access_in_one_release.get(indexR));
//            }
//        }
//
//        return number_of_request_by_HP;
//    }
//
//    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, long[] Ris, long Ri) {
//        int number_of_request_by_Remote_P = 0;
//
//        for (int i = 0; i < tasks.size(); i++) {
//            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
//                SporadicTask remote_task = tasks.get(i);
//                int indexR = getIndexRInTask(remote_task, resource);
//                number_of_request_by_Remote_P += Math.ceil((double) (Ri + Ris[i]) / (double) remote_task.period)
//                        * remote_task.number_of_access_in_one_release.get(indexR);
//            }
//        }
//        return number_of_request_by_Remote_P;
//    }
//
//    /*
//     * Return the index of a given resource in stored in a task.
//     */
//    private int getIndexRInTask(SporadicTask task, Resource resource) {
//        int indexR = -1;
//        if (task.resource_required_index.contains(resource.id - 1)) {
//            for (int j = 0; j < task.resource_required_index.size(); j++) {
//                if (resource.id - 1 == task.resource_required_index.get(j)) {
//                    indexR = j;
//                    break;
//                }
//            }
//        }
//        return indexR;
//    }
//
//}
