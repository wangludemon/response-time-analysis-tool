package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class MSRPForModeSwitch {
    public static Logger log = LogManager.getLogger();
    long count = 0;

    long overhead = (long) (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
    long CX1 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH1;
    long CX2 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH2;

    public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks, boolean printDebug) {
        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);
        long[][] response_time = new long[tasks.size()][];
        boolean isEqual = false, missDeadline = false;
        count = 0;

        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }

        new AnalysisUtils().cloneList(init_Ri, response_time);

        for (int i = 0; i < lowTasks.size(); i++) {
            for (int j = 0; j < lowTasks.get(i).size(); j++) {
                SporadicTask task = lowTasks.get(i).get(j);
                task.spin = resourceAccessingTimeForLowTask(task, resources);
            }
        }

        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            long[][] response_time_plus = busyWindow(tasks, resources, lowTasks, response_time, true);

            for (int i = 0; i < response_time_plus.length; i++) {
                for (int j = 0; j < response_time_plus[i].length; j++) {
                    if (response_time[i][j] != response_time_plus[i][j])
                        isEqual = false;

                    if (response_time_plus[i][j] > tasks.get(i).get(j).deadline)
                        missDeadline = true;
                }
            }

            count++;
            new AnalysisUtils().cloneList(response_time_plus, response_time);

            if (missDeadline)
                break;
        }

        if (printDebug) {
            if (missDeadline)
                System.out.println("FIFONP JAVA    after " + count + " tims of recursion, the tasks miss the deadline.");
            else
                System.out.println("FIFONP JAVA    after " + count + " tims of recursion, we got the response time.");

            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks, long[][] response_time, boolean btbHit) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);

                long exec_preempted_T = getSpinDelay(task, tasks, lowTasks, resources, response_time[i][j], response_time);
                task.interference = highPriorityInterference(task, tasks, lowTasks, response_time[i][j]);
                task.local = localBlocking(task, tasks, lowTasks, resources, response_time, response_time[i][j]);

                response_time_plus[i][j] = task.Ri = task.WCET + task.spin + task.indirect_spin + task.interference + task.local + exec_preempted_T + CX1;

                if (task.Ri > task.deadline)
                    return response_time_plus;

            }
        }
        return response_time_plus;
    }

    private long resourceAccessingTimeForLowTask(SporadicTask t, ArrayList<Resource> resources) {
        long spin_delay = 0;
        for (int k = 0; k < t.resource_required_index.size(); k++) {
            Resource resource = resources.get(t.resource_required_index.get(k));
            spin_delay += resource.partitions.size() * (resource.csl_low + overhead) * t.number_of_access_in_one_release.get(k);
        }
        return spin_delay;
    }

    /*
     * Calculate the spin delay for a given task t.
     */
    private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris) {
        long spin = 0;
        long indirect_spin = 0, direct_spin = 0;
        long PWLP_S = 0;
        long exec_and_preempted_T = 0;

        ArrayList<ArrayList<ArrayList<Long>>> requestsLeftOnRemoteP = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            requestsLeftOnRemoteP.add(new ArrayList<>());
            Resource res = resources.get(i);
            ArrayList<Long> temp = getSpinDelayForOneResource(task, tasks, LowTasks, res, time, Ris, requestsLeftOnRemoteP.get(i));
            indirect_spin += temp.get(0);
            direct_spin += temp.get(1);
            exec_and_preempted_T += temp.get(2);
        }

        task.indirect_spin = indirect_spin;
        task.spin = direct_spin;

        return exec_and_preempted_T;
    }

    private ArrayList<Long> getSpinDelayForOneResource(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris,
                                                       ArrayList<ArrayList<Long>> requestsLeftOnRemoteP) {
        long spin = 0;
        long ncs = 0;
        long ncs_lo = 0;
        long ncs_hi = 0;
        long N_i_k = 0;

        long indirect_spin = 0;
        long direct_spin = 0;

        // 任务自身访问资源的次数
        if (task.resource_required_index.contains(resource.id - 1)) {
            N_i_k += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));
        }


        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + hpTask.Ri_LO) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_h_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_hi += (long) Math.ceil((double) (time + Ris[hpTask.partition][i]) / (double) hpTask.period) * n_h_k;
            }
        }

        ncs = N_i_k + ncs_lo + ncs_hi;

        // RBTQ项的计算
        if (ncs > 0) {
            for (int i = 0; i < tasks.size(); i++) {
                // 遍历所有远程处理器
                if (task.partition != i) {
                    /* For each remote partition */
                    long number_of_low_request_by_Remote_P = 0;
                    long number_of_high_request_by_Remote_P = 0;
                    // 远程处理器HI任务访问资源次数
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            long number_of_release_hi = (long) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
                            number_of_high_request_by_Remote_P += number_of_release_hi * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }
                    // 远程处理器LO任务访问资源次数
                    for (int j = 0; j < LowTasks.get(i).size(); j++) {
                        if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = LowTasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + remote_task.Ri_LO) / (double) remote_task.period);
                            number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }

                    long possible_spin_delay = Long.min(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P, ncs);

                    //min{local, remote m}
                    long indirect_remote_times = number_of_high_request_by_Remote_P - ncs;
                    indirect_spin += indirect_remote_times > 0 ?
                            ncs * (resource.csl_high + overhead) : number_of_high_request_by_Remote_P * (resource.csl_high + overhead) + Long.min(Math.abs(indirect_remote_times), number_of_low_request_by_Remote_P) * (resource.csl_low + overhead);

                    if (number_of_high_request_by_Remote_P > ncs) {
                        indirect_spin += ncs * (resource.csl_high + overhead);
                    } else {
                        indirect_spin += number_of_high_request_by_Remote_P * (resource.csl_high + overhead) + Long.min(Math.abs(number_of_high_request_by_Remote_P - ncs), number_of_low_request_by_Remote_P) * (resource.csl_low + overhead);
                    }
                    //min{N, max(remote_m-local_higher,0)}
//                    long direct_remote_times_judge = Long.max(indirect_remote_times, 0) + Long.min(N_i_k, Long.max(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P - ncs, 0))
//                            - number_of_high_request_by_Remote_P;
//                    direct_spin += direct_remote_times_judge > 0 ?
//                            Long.min(direct_remote_times_judge, number_of_low_request_by_Remote_P) * resource.csl
//                            : Long.min(Math.abs(direct_remote_times_judge), number_of_high_request_by_Remote_P) * resource.csl_high;

                    if (number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P - ncs <= 0)
                        direct_spin += 0;
                    else if (indirect_remote_times <= 0)
                        direct_spin += Long.min(Long.max(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P - ncs, 0), N_i_k) * (resource.csl_low + overhead);
                    else
                        direct_spin += indirect_remote_times > N_i_k ? N_i_k * (resource.csl_high + overhead)
                                : indirect_remote_times * (resource.csl_high + overhead) + Long.min(N_i_k - indirect_remote_times, number_of_low_request_by_Remote_P) * (resource.csl_low + overhead);


                    // 建立RBTQ
                    ArrayList<Long> RBTQ = new ArrayList<>();
                    while (number_of_high_request_by_Remote_P > 0) {
                        RBTQ.add((resource.csl_high + overhead));
                        number_of_high_request_by_Remote_P--;
                    }
                    //min{local, remote m}
                    while (number_of_low_request_by_Remote_P > 0) {
                        RBTQ.add((resource.csl_low + overhead));
                        number_of_low_request_by_Remote_P--;
                    }


                    while (possible_spin_delay > 0) {
                        spin += RBTQ.remove(0);
                        possible_spin_delay--;
                    }
                    // 这个处理器还剩余次数的话加入
                    if (RBTQ.size() > 0)
                        requestsLeftOnRemoteP.add(RBTQ);
                }
            }
        }
        //<indirect,direct, preempted+exec>
        ArrayList<Long> spin_all = new ArrayList<>();
        spin_all.add(indirect_spin);
        spin_all.add(direct_spin);
        spin_all.add(N_i_k * (resource.csl_high + overhead) + ncs_lo * (resource.csl_low + overhead) + ncs_hi * (resource.csl_high + overhead));

        return spin_all;
    }


    /*
     * Calculate interference for a given task t.
     * Including HI tasks and LO tasks
     */
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, long time) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> TasksPartition = tasks.get(partition);
        ArrayList<SporadicTask> LowTasksPartition = LowTasks.get(partition);
        // HI Task 部分
        for (SporadicTask hpTask : TasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET + CX2);
            }
        }
        // LO Task 部分
        for (SporadicTask hpTask : LowTasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (t.Ri_LO) / (double) hpTask.period) * (hpTask.WCET + CX2);
            }
        }
        return interference;
    }

    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> lowtasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
        ArrayList<Resource> LocalBlockingResources_LO = getLocalBlockingResources(t, resources, lowtasks);
        ArrayList<Resource> LocalBlockingResources_HI = getLocalBlockingResources(t, resources, tasks);
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources_LO.size(); i++) {
            Resource res = LocalBlockingResources_LO.get(i);
            long local_blocking = res.csl_low + getRemoteBlockingTime(t, tasks, lowtasks, LocalBlockingResources_LO, Ri, Ris);
            local_blocking_each_resource.add(local_blocking);
        }
        for (int i = 0; i < LocalBlockingResources_HI.size(); i++) {
            Resource res = LocalBlockingResources_HI.get(i);
            long local_blocking = res.csl_high + getRemoteBlockingTime(t, tasks, lowtasks, LocalBlockingResources_HI, Ri, Ris);
            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Long.compare(l1, l2));

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> getLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> tasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;
        // low mode
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            // local resources that have a higher ceiling
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                    && resource.getCeilingForProcessor(tasks.get(task.partition)) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    if (tasks.contains(resource.requested_tasks.get(j))) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        System.out.println("LO" + LP_task.C_LOW + "HI" + LP_task.C_HIGH);
                        if (LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
            }
            // global resources that are accessed from the partition
            if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    if (tasks.contains(resource.requested_tasks.get(j))) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        System.out.println("LO" + LP_task.C_LOW + "HI" + LP_task.C_HIGH);
                        if (LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
            }
        }
        return localBlockingResources;
    }

    /*
     * Return Sum (Pm!=P(ti)) RBTQ(ncs)
     */
    private long getRemoteBlockingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris) {
        long BlockingTime = 0;

        for (int i = 0; i < resources.size(); i++) {
            Resource res = resources.get(i);
            ArrayList<ArrayList<Long>> RBTQs = getRBTQs(task, tasks, LowTasks, res, time, Ris);
            long ncs = getNcs(task, tasks, LowTasks, res, time, Ris);
            // Sum (Pm!=P(ti)) RBTQ(ncs)
            for (int m = 0; m < tasks.size(); m++) {
                if (task.partition != m) {
                    var RBTQ = RBTQs.get(m);
                    BlockingTime += RBTQ.get((int) ncs);
                }
            }
        }
        return BlockingTime;
    }

    /*
     * getRBTQs返回在模式切换过程中，在 ti 响应时间内，所有remote processor上访问资源rk 的远程阻塞时间队列（RBTQs）
     * RBTQ表示在模式切换过程中，在 ti 响应时间内，remote processor m 上访问资源rk 的远程阻塞时间队列（RBTQ）
     */
    private ArrayList<ArrayList<Long>> getRBTQs(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris) {

        ArrayList<ArrayList<Long>> RBTQs = new ArrayList<>();
        long number_of_request_by_Remote_P = 0;
        // RBTQ项的计算
        for (int i = 0; i < tasks.size(); i++) {
            // 遍历所有远程处理器
            if (task.partition != i) {
                /* For each remote partition */
                long number_of_low_request_by_Remote_P = 0;
                long number_of_high_request_by_Remote_P = 0;
                // 远程处理器HI任务访问资源次数
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = tasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        long number_of_release_hi = (long) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
                        number_of_high_request_by_Remote_P += number_of_release_hi * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                // 远程处理器LO任务访问资源次数
                for (int j = 0; j < LowTasks.get(i).size(); j++) {
                    if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = LowTasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + remote_task.Ri_LO) / (double) remote_task.period);
                        number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }

                // 建立RBTQ
                var RBTQ = new ArrayList<Long>();
                while (number_of_high_request_by_Remote_P > 0) {
                    RBTQ.add((resource.csl_high + overhead));
                    number_of_high_request_by_Remote_P--;
                }
                //min{local, remote m}
                while (number_of_low_request_by_Remote_P > 0) {
                    RBTQ.add((resource.csl_low + overhead));
                    number_of_low_request_by_Remote_P--;
                }
                RBTQs.add(RBTQ);
            }

        }
        return RBTQs;
    }

    /*
     * 返回在模式切换过程中，local processor 上的高优先级任务在 τi 响应时间内访问资源 rk 的次数 + 任务 τi 执行资源 rk 的次数
     */
    private Long getNcs(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris) {
        long ncs = 0;
        long ncs_lo = 0;
        long ncs_hi = 0;
        long N_i_k = 0;

        // 任务自身访问资源的次数
        if (task.resource_required_index.contains(resource.id - 1)) {
            N_i_k += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));
        }


        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + hpTask.Ri_LO) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_h_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_hi += (long) Math.ceil((double) (time + Ris[hpTask.partition][i]) / (double) hpTask.period) * n_h_k;
            }
        }

        ncs = N_i_k + ncs_lo + ncs_hi;
        return ncs;
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
}
