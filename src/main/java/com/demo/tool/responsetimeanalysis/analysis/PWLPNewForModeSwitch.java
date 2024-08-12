package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**  PWLP analysis from xxx
 *  Response time including:
 *  WCET
 *  Resource execution time
 *  interference: preemption time : WCET + res exe
 *  spin Blocking: direct and indirect spin blocking
 *  arrival blocking **/

public class PWLPNewForModeSwitch {
    public static Logger log = LogManager.getLogger();
    long count = 0;
    long overhead = (long) (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
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


        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            long[][] response_time_plus = busyWindow(tasks, lowTasks, resources, response_time);

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
                System.out.println("FIFO-P-NEW    after " + count + " tims of recursion, the tasks miss the deadline.");
            else
                System.out.println("FIFO-P-NEW    after " + count + " tims of recursion, we got the response time.");
            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> lowTasks, ArrayList<Resource> resources, long[][] response_time) {
        long[][] response_time_plus = new long[tasks.size()][];


        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);

                task.spin_delay_by_preemptions = 0;
                long spin = getSpinDelay(task, tasks, lowTasks, resources, response_time[i][j], response_time);
                task.interference = highPriorityInterference(task, tasks, lowTasks, response_time[i][j]);
                task.local = localBlocking(task, tasks, lowTasks, resources, response_time, response_time[i][j]);

                response_time_plus[i][j] = task.Ri = task.WCET + task.prec_HIGH + task.spin + task.indirect_spin + task.PWLP_S + task.interference + task.local  + CX1;
                if (task.Ri > task.deadline)
                    return response_time_plus;

            }
        }
        return response_time_plus;
    }

    private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris) {
        long spin = 0;
        long indirect_spin = 0, direct_spin = 0;
        long PWLP_S = 0;
        //long exec_and_preempted_T = 0;

        ArrayList<ArrayList<ArrayList<Long>>> requestsLeftOnRemoteP = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            requestsLeftOnRemoteP.add(new ArrayList<>());
            Resource res = resources.get(i);
            ArrayList<Long> temp = getSpinDelayForOneResoruce(task, tasks, LowTasks, res, time, Ris, requestsLeftOnRemoteP.get(i));
            indirect_spin += temp.get(0);
            direct_spin += temp.get(1);
            //exec_and_preempted_T += temp.get(2);
        }



        long preemptions = 0;
        long request_by_preemptions = 0;
        // 计算抢占次数
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority) {
                preemptions += (long) Math.ceil((double) (task.Ri_LO) / (double) hpTask.period);
            }
        }
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority) {
                preemptions += (long) Math.ceil((double) (time) / (double) hpTask.period);
            }
        }

        // 计算任务及本地高优先级任务可能访问的资源集合
        Set<Integer> resourceIndexSet = new HashSet<>();
        resourceIndexSet.addAll(task.resource_required_index);
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            if (tasks.get(task.partition).get(i).priority > task.priority) {
                resourceIndexSet.addAll(tasks.get(task.partition).get(i).resource_required_index);
            }
        }
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            if (LowTasks.get(task.partition).get(i).priority > task.priority) {
                resourceIndexSet.addAll(LowTasks.get(task.partition).get(i).resource_required_index);
            }
        }

        while (preemptions > 0) {

            long max_delay = 0;
            int max_delay_resource_index = -1;
            for (int i = 0; i < resources.size(); i++) {
                if (!resourceIndexSet.contains(i)) {
                    continue;
                }
                long cancel_spin = 0;
                // 对于每个资源，每个远程处理器上的阻塞队列
                if (requestsLeftOnRemoteP.get(i).size() == 0) {
                    cancel_spin = 0;
                } else {
                    // 还有几个远程处理器上有阻塞队列，且这些队列里的元素必然是大于1的
                    for (int m = 0; m < requestsLeftOnRemoteP.get(i).size(); m++) {
                        cancel_spin += requestsLeftOnRemoteP.get(i).get(m).get(0);
                    }
                }
                if (max_delay < cancel_spin) {
                    max_delay = cancel_spin;
                    max_delay_resource_index = i;
                }
            }

            if (max_delay > 0) {
                PWLP_S += max_delay;
                spin += max_delay;
                for (int i = 0; i < requestsLeftOnRemoteP.get(max_delay_resource_index).size(); i++) {
                    requestsLeftOnRemoteP.get(max_delay_resource_index).get(i).remove(0);
                    if (requestsLeftOnRemoteP.get(max_delay_resource_index).get(i).size() < 1) {
                        requestsLeftOnRemoteP.get(max_delay_resource_index).remove(i);
                        i--;
                    }
                }
                preemptions--;
                request_by_preemptions++;
            } else
                break;
        }

        task.PWLP_S = PWLP_S;
        task.indirect_spin = indirect_spin;
        task.spin = direct_spin;
        task.spin_delay_by_preemptions = request_by_preemptions;

        return indirect_spin+direct_spin;
    }

    private ArrayList<Long> getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris,
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

        ncs = ncs_lo + ncs_hi;  // 高优先级任务的访问次数

        // RBTQ项的计算
        if (ncs > 0 || N_i_k > 0) {
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
                    long remote_request = number_of_high_request_by_Remote_P+number_of_low_request_by_Remote_P;
                    long possible_spin_delay = Long.min(remote_request, ncs);
                    long direct_spin_delay = Long.min(remote_request-possible_spin_delay, N_i_k);

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
                        indirect_spin += RBTQ.remove(0);
                        possible_spin_delay--;
                    }
                    while(direct_spin_delay > 0 ){
                        direct_spin += RBTQ.remove(0);
                        direct_spin_delay--;
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

        return spin_all;
    }

    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, long time) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> TasksPartition = tasks.get(partition);
        ArrayList<SporadicTask> LowTasksPartition = LowTasks.get(partition);
        // HI Task 部分
        for (SporadicTask hpTask : TasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (time) / (double) hpTask.period ) * (hpTask.WCET + hpTask.prec_HIGH + CX2);
            }
        }
        // LO Task 部分
        for (SporadicTask hpTask : LowTasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (t.Ri_LO) / (double) hpTask.period) * (hpTask.WCET + hpTask.prec_LOW + CX2);
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
            long local_blocking = res.csl_low;
            local_blocking_each_resource.add(local_blocking);
        }
        for (int i = 0; i < LocalBlockingResources_HI.size(); i++) {
            Resource res = LocalBlockingResources_HI.get(i);
            long local_blocking = res.csl_high;
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
