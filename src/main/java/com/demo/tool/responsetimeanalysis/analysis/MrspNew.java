package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * MrsP without np
 **/
public class MrspNew {
    public static Logger log = LogManager.getLogger();
    static long Cnp = 50;
    static double Cmig = 6;
    long count = 0;
    long overhead = (long) (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
    long CX1 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH1;
    long CX2 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH2;

    public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, boolean btbHit, boolean printDebug) {

        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);

        long[][] response_time = new long[tasks.size()][];
        boolean isEqual = false, missDeadline = false;
        count = 0;

        // Cnp 的初始化
        // 所有使用MrsP协议的资源中最大的csl作为npsection的值
        long npsection = 0;
        for (Resource resource : resources) {
            if (npsection < resource.csl) npsection = resource.csl;
        }
        Cnp = 50;

        // Cmig的初始化
        //Cmig = AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION;
        Cmig =6;

        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }

        new AnalysisUtils().cloneList(init_Ri, response_time);

        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            long[][] response_time_plus = busyWindow(tasks, resources, response_time, btbHit);

            for (int i = 0; i < response_time_plus.length; i++) {
                for (int j = 0; j < response_time_plus[i].length; j++) {
                    if (response_time[i][j] != response_time_plus[i][j]) isEqual = false;
                    if (response_time_plus[i][j] > tasks.get(i).get(j).deadline) missDeadline = true;
                }
            }

            count++;
            new AnalysisUtils().cloneList(response_time_plus, response_time);

            if (missDeadline) break;
        }

        if (printDebug) {
            if (missDeadline)
                System.out.println("FIFO-P-NEW    after " + count + " tims of recursion, the tasks miss the deadline.");
            else System.out.println("FIFO-P-NEW    after " + count + " tims of recursion, we got the response time.");
            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, boolean btbHit) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);
                task.spin_delay_by_preemptions = 0;
                long Ei = getSpinDelay(task, tasks, resources, response_time[i][j], response_time, btbHit);
                task.interference = highPriorityInterference(task, tasks, response_time[i][j], response_time, resources);
                task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j], btbHit);
                var migrateCost = getMigrateCost(task, tasks, resources, response_time[i][j], response_time_plus, btbHit);

                response_time_plus[i][j] = task.Ri = task.WCET + Ei + migrateCost + task.local + task.interference + CX1;

                if (task.Ri > task.deadline) return response_time_plus;

            }
        }
        return response_time_plus;
    }

    /**
     * @param task:
     * @param tasks:
     * @param resources:
     * @param time:
     * @param Ris:
     * @param btbHit:
     * @return long
     * @description: 由于 MrsP 协议中的迁移机制造成额外迁移 (阻塞) 时间
     * @author: Chen
     * @date: 2023/10/12 21:00
     */
    private long getMigrateCost(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long time, long[][] Ris, boolean btbHit) {
        long migrateCost = 0;
        migrateCost += getMIG(task, tasks, resources, time, 0, time, Ris, btbHit);

        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);// local优先级高于tau_i的任务
            //判断关键级
            //if(hpTask.critical == mode)
            migrateCost += getMIG(hpTask, tasks, resources, time, Ris[hpTask.partition][i], time, Ris, btbHit);
        }

        return migrateCost;
    }

    /**
     * @param task:
     * @param tasks:
     * @param resources:
     * @param duration:
     * @param jitter:
     * @param time:
     * @param Ris:
     * @param btbHit:
     * @return long
     * @description: MIG
     * @author: Chen
     * @date: 2023/10/13 22:58
     */

    private long getMIG(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, double duration, long jitter, long time, long[][] Ris, boolean btbHit) {
        long MIG = 0;
        for (int resIndex : task.resource_required_index) {
            var resource = resources.get(resIndex);
            var Nxk = task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));
            for (int n = 1; n < Math.ceil((duration + jitter) / task.period) * Nxk; n++) {
                var mt = getMTSet(task, tasks, resource, n, time, Ris, btbHit);
                MIG += getMig(task, tasks, mt, resource, time, Ris, btbHit);
            }
        }
        return MIG;
    }


    /**
     * @param task:
     * @param tasks:
     * @param resource:
     * @param time:
     * @param Ris:
     * @param btbHit:
     * @return long
     * @description: 表示在关键模式 L 下，若任务 τi 访问由 MrsP 协议管理的资源时，可能由于 MrsP 协议中的迁移机制造成额外迁移 (阻塞) 时间
     * @author: Chen
     * @date: 2023/10/10 17:53
     */
    private long getMig(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, HashSet<Integer> partitionSet, Resource resource, long time, long[][] Ris, boolean btbHit) {
        long mig = 0;
        var mt = partitionSet;
        var mtp = getMtpSet(tasks, resource, mt);
        for (int partition : mt) {
            double cost = 0;
            if ((mt.size() == 1 && mt.contains(partition)) || !mtp.contains(partition)) {
                cost = 0;
            } else if (mt.size() > 1 && (mtp.size() == 1 && mtp.contains(partition))) {
                cost = 2 * Cmig;
            } else {
                double Cnp = task.np_section;
                var Mnp = getMnp(resource, Cnp);
                var Mhp = MhpBusyWindow(task, tasks, mt, resource, Mnp);
                cost = (long) Math.min(Mhp, Mnp);
            }
            mig += cost;
        }
        return mig;
    }

    /**
     * @param task:
     * @param tasks:
     * @param migration_targets_with_P:
     * @param resource:
     * @param migByNP:
     * @return double
     * @description: 关键模式 L 时使用非 NP-section 方案的最坏开销窗口
     * @author: Chen
     * @date: 2023/10/13 22:54
     */
    private double MhpBusyWindow(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, HashSet<Integer> migration_targets_with_P, Resource resource, double migByNP) {
        double migCost = 0;

        double newMigCost = MhpOneCal(migration_targets_with_P, resource.csl + migCost, resource, tasks);

        while (migCost != newMigCost) {
            migCost = newMigCost;
            newMigCost = MhpOneCal(migration_targets_with_P, resource.csl + migCost, resource, tasks);

            if (newMigCost > task.deadline) {
                return newMigCost;
            }
            if (migByNP > 0 && newMigCost > migByNP) {
                return newMigCost;
            }
        }

        return migCost;
    }

    /**
     * @param migration_targets_with_P:
     * @param duration:
     * @param resource:
     * @param tasks:
     * @return double
     * @description: 关键模式 L 时使用非 NP-section 方案的最坏开销
     * @author: Chen
     * @date: 2023/10/13 22:56
     */

    private double MhpOneCal(HashSet<Integer> migration_targets_with_P, double duration, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks) {
        double migCost = 0;

        for (int partition_with_p : migration_targets_with_P) {

            for (int j = 0; j < tasks.get(partition_with_p).size(); j++) {
                SporadicTask hpTask = tasks.get(partition_with_p).get(j);

                if (hpTask.priority > resource.getCeilingForProcessor(tasks, partition_with_p))
                    migCost += Math.ceil((duration) / hpTask.period) * Cmig;

            }
        }

        return migCost + Cmig;
    }

    /**
     * @param resource:
     * @param Cnp:
     * @return double
     * @description: 关键模式 L 时使用 NP-section 方案（即每次迁移都强制执行一个 Cnp）的最坏开销
     * @author: Chen
     * @date: 2023/10/13 22:58
     */
    public double getMnp(Resource resource, double Cnp) {
        return Cmig * (Math.ceil(resource.csl / Cnp) + 1);
    }

    /**
     * @param tasks:
     * @return java.util.ArrayList<java.lang.Integer>
     * @description: 给定迁移目标集合 mt 和资源 rk, 可能存在访问资源时抢占的处理器集合
     * @author: Chen
     * @date: 2023/10/10 17:24
     */
    private ArrayList<Integer> getMtpSet(ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, HashSet<Integer> mt) {
        ArrayList<Integer> mtp = new ArrayList<Integer>();
        for (int partition : mt) {
            if (getHPTSet(tasks, resource, partition).size() != 0) {
                mtp.add(partition);
            }
        }
        return mtp;
    }

    /**
     * @param tasks:
     * @param resource:
     * @return java.util.HashSet<responseTimeTool.entity.SporadicTask>
     * @description: tasks on partition m that have a higher priority than the ceiling of rk.
     * @author: Chen
     * @date: 2023/9/24 1:16
     */
    private HashSet<SporadicTask> getHPTSet(ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, int partition) {
        var hptSet = new HashSet<SporadicTask>();
        var hptasks = tasks.get(partition);
        for (int i = 0; i < hptasks.size(); i++) {
            if (hptasks.get(i).resource_required_index.contains(resource.id - 1) && hptasks.get(i).priority > resource.getCeilingForProcessor(tasks, partition))
                hptSet.add(hptasks.get(i));
        }
        return hptSet;
    }

    /**
     * @param task:
     * @param tasks:
     * @param resource:
     * @param nth:
     * @param time:
     * @param Ris:
     * @param btbHit:
     * @return java.util.HashSet<java.lang.Integer>
     * @description: 返回在关键模式 L 下，任务 τx 在发布期间第 n 次访问资源 rk 时可能的迁移目标处理器集合
     * @author: Chen
     * @date: 2023/9/24 0:39
     */
    private HashSet<Integer> getMTSet(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, int nth, long time, long[][] Ris, boolean btbHit) {
        var processers = new HashSet<Integer>();
        var partitions = resource.partitions;
        for (Integer partition : partitions) {
            var nrp = getNumberOfRequestByRemoteP(task, tasks, resource, partition, time, Ris, btbHit);
            var nlp = getNumberOfRequestByLocalP(task, tasks, resource, time, Ris, btbHit);
            if (task.partition != partition && nrp - nlp - nth + 1 > 0) {
                processers.add(partition);
            }
        }
        processers.add(task.partition);
        return processers;
    }

    /**
     * @param task:
     * @param tasks:
     * @param resource:
     * @param time:
     * @param Ris:
     * @param btbHit:
     * @return long
     * @description: 返回表示在关键模式 L 下，local processor m 上的任务在 τi 响应时间内访问资源 rk 的次数
     * @author: Chen
     * @date: 2023/9/24 0:38
     */
    private long getNumberOfRequestByLocalP(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, long time, long[][] Ris, boolean btbHit) {
        long numberOfRequestByLocalP = 0;
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);// local优先级高于tau_i的任务
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                numberOfRequestByLocalP += (long) Math.ceil((double) (time + (btbHit ? Ris[hpTask.partition][i] : 0)) / (double) hpTask.period) * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }
        return numberOfRequestByLocalP;
    }

    /**
     * @param task:
     * @param tasks:
     * @param resource:
     * @param time:
     * @param Ris:
     * @param btbHit:
     * @return long
     * @description: 返回表示在关键模式 L 下，remote processor m 上的任务在 τi 响应时间内访问资源 rk 的次数
     * @author: Chen
     * @date: 2023/9/24 0:40
     */
    private long getNumberOfRequestByRemoteP(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, int partition, long time, long[][] Ris, boolean btbHit) {

        long number_of_request_by_Remote_P = 0;
        //remote核心的请求次数

        /* For each remote partition */
        for (int j = 0; j < tasks.get(partition).size(); j++) {
            if (tasks.get(partition).get(j).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(partition).get(j);
                int indexR = getIndexRInTask(remote_task, resource);
                int number_of_release = (int) Math.ceil((double) (time + (btbHit ? Ris[partition][j] : 0)) / (double) remote_task.period);
                number_of_request_by_Remote_P += (long) number_of_release * remote_task.number_of_access_in_one_release.get(indexR);

            }


        }
        return number_of_request_by_Remote_P;
    }

    private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long time, long[][] Ris, boolean btbHit) {
        long spin = 0;
        long indirect_spin = 0, direct_spin = 0;
        long exec_and_preempted_T = 0;

        ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            requestsLeftOnRemoteP.add(new ArrayList<Long>());
            Resource res = resources.get(i);
            ArrayList<Long> temp = getSpinDelayForOneResoruce(task, tasks, res, time, Ris, requestsLeftOnRemoteP.get(i), btbHit);
            indirect_spin += temp.get(0);
            direct_spin += temp.get(1);
            exec_and_preempted_T += temp.get(2);
        }

        task.indirect_spin = indirect_spin;
        task.spin = direct_spin;

        return exec_and_preempted_T;
    }

    //E
    private ArrayList<Long> getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, long time, long[][] Ris, ArrayList<Long> requestsLeftOnRemoteP, boolean btbHit) {
        long spin = 0;
        long ncs = 0;

        long zeta = 0;

        long n = 0;
        long indirect_spin = 0;
        long direct_spin = 0;

        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);// local优先级高于tau_i的任务
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                zeta += (int) Math.ceil((double) (time + (btbHit ? Ris[hpTask.partition][i] : 0)) / (double) hpTask.period) * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }
        ncs = zeta;
        //N_i
        if (task.resource_required_index.contains(resource.id - 1))
            n += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));

        ncs += n;
        //remote核心的请求次数
        if (ncs > 0) {
            for (int i = 0; i < tasks.size(); i++) {
                if (task.partition != i) {
                    /* For each remote partition */
                    long number_of_request_by_Remote_P = 0;
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            int number_of_release = (int) Math.ceil((double) (time + (btbHit ? Ris[i][j] : 0)) / (double) remote_task.period);
                            number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);

                        }
                    }
                    //min{local, remote m}
                    indirect_spin += Long.min(zeta, number_of_request_by_Remote_P) * (resource.csl + overhead);
                    //min{N, max(remote_m-local_higher,0)}
                    direct_spin += Long.min(n, Long.max(number_of_request_by_Remote_P - zeta, 0)) * (resource.csl + overhead);

                    long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);

                    spin += possible_spin_delay;
                    if (number_of_request_by_Remote_P - ncs > 0)    // L做准备
                        requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
                }
            }
        }
        //<indirect,direct, preempted+exec>
        ArrayList<Long> spin_all = new ArrayList<>();
        spin_all.add(indirect_spin);
        spin_all.add(direct_spin);
        spin_all.add(ncs * (resource.csl + overhead));
//        spin * resource.csl + ncs * resource.csl;
        return spin_all;
    }

    /*
     * Calculate the local high priority tasks' interference for a given task t.
     * CI is a set of computation time of local tasks, including spin delay.
     */
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long time, long[][] Ris, ArrayList<Resource> resources) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET + CX2);
            }
        }
        return interference;
    }

    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri, Boolean btbHit) {
        ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, tasks.get(t.partition));
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;

            if (res.isGlobal) {
                var alpha = new HashSet<Integer>();
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    alpha.add(partition);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit);
                    int norT = t.resource_required_index.contains(res.id - 1) ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1)) : 0;
                    int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], Ri, btbHit);

                    if (partition != t.partition && (norHP + norT) < norR) {
                        local_blocking += res.csl;
                    }
                }
                local_blocking += getMig(t, tasks, alpha, res, Ri, Ris, btbHit);
            }
            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> getLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
            // global resources that are accessed from the partition
            if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
        }

        return localBlockingResources;
    }

    /*
     * Return the index of a given resource in stored in a task.
     */
    private int getIndexRInTask(SporadicTask task, Resource resource) {
        int indexR = -1;
        if (task.resource_required_index.contains(resource.id - 1)) {
            for (int j = 0; j < task.resource_required_index.size(); j++) {
                if (resource.id - 1 == task.resource_required_index.get(j)) {
                    indexR = j;
                    break;
                }
            }
        }
        return indexR;
    }

    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit) {
        int number_of_request_by_Remote_P = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? Ris[i] : 0)) / (double) remote_task.period) * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_Remote_P;
    }

    /*
     * gives that number of requests from HP local tasks for a resource that is
     * required by the given task.
     */
    private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit) {
        int number_of_request_by_HP = 0;
        int priority = task.priority;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask hpTask = tasks.get(i);
                int indexR = getIndexRInTask(hpTask, resource);
                number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? Ris[i] : 0)) / (double) hpTask.period) * hpTask.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_HP;
    }

    private int getMaxPriorityOfPartition(ArrayList<ArrayList<SporadicTask>> tasks, int partition) {
        ArrayList<SporadicTask> taskset = tasks.get(partition);
        int max_priority = -1;
        for (SporadicTask t : taskset) {
            if (t.priority > max_priority) {
                max_priority = t.priority;
            }
        }
        return max_priority;
    }
}
