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

public class PWLPNew {
    public static Logger log = LogManager.getLogger();
    long count = 0;

    long overhead = (long) (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
    long CX1 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH1;
    long CX2 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH2;

    public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, boolean btbHit, boolean printDebug) {
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
            long[][] response_time_plus = busyWindow(tasks, resources, response_time, btbHit);

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

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, boolean btbHit) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);
                task.spin_delay_by_preemptions = 0;
                long spin_delay = getSpinDelay(task, tasks, resources, response_time[i][j], response_time, btbHit);
                task.interference = highPriorityInterference(task, tasks, response_time[i][j], response_time, resources);
                task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j]);

                response_time_plus[i][j] = task.Ri = task.WCET + task.pure_resource_execution_time + spin_delay + task.PWLP_S + task.interference + task.local + CX1;

                if (task.Ri > task.deadline)
                    return response_time_plus;

            }
        }
        return response_time_plus;
    }

    private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long time, long[][] Ris,
                              boolean btbHit) {
        long spin = 0;
        long indirect_spin = 0, direct_spin = 0;
        long PWLP_S = 0;
        long exec_and_preempted_T = 0;

        ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            requestsLeftOnRemoteP.add(new ArrayList<Long>());
            Resource res = resources.get(i);
            ArrayList<Long> temp = getSpinDelayForOneResoruce(task, tasks, res, time, Ris, requestsLeftOnRemoteP.get(i), btbHit);
            indirect_spin += temp.get(0);
            direct_spin += temp.get(1);

        }



        // preemptions
        long preemptions = 0;    // local优先级高于tau_i的任务，在tau_i响应时间内访问r^k的次数
        long request_by_preemptions = 0;
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            if (tasks.get(task.partition).get(i).priority > task.priority) {
                preemptions += (int) Math.ceil((double) (time) / (double) tasks.get(task.partition).get(i).period);
            }
        }

        // local优先级高于tau_i的任务
        Set<Integer> resourceIndexSet = new HashSet<>();    // local优先级高于tau_i的任务 所需要的r
        resourceIndexSet.addAll(task.resource_required_index);
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            if (tasks.get(task.partition).get(i).priority > task.priority) {
                resourceIndexSet.addAll(tasks.get(task.partition).get(i).resource_required_index);
            }
        }

        while (preemptions > 0) {

            long max_delay = 0;
            int max_delay_resource_index = -1;
            for (int i = 0; i < resources.size(); i++) {
                if (max_delay < (resources.get(i).csl + overhead) * requestsLeftOnRemoteP.get(i).size()) {
                    max_delay = (resources.get(i).csl + overhead) * requestsLeftOnRemoteP.get(i).size();
                    max_delay_resource_index = i;
                }
            }

            if (max_delay > 0) {
                PWLP_S += max_delay;
                spin += max_delay;
                for (int i = 0; i < requestsLeftOnRemoteP.get(max_delay_resource_index).size(); i++) {
                    requestsLeftOnRemoteP.get(max_delay_resource_index).set(i, requestsLeftOnRemoteP.get(max_delay_resource_index).get(i) - 1);
                    if (requestsLeftOnRemoteP.get(max_delay_resource_index).get(i) < 1) {
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

        return task.indirect_spin + task.spin;
    }

    //E 返回一个资源的spin 三个变量，(indirect spin, direct spin, 任务自身的（删除））
    private ArrayList<Long> getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, long time, long[][] Ris,
                                                       ArrayList<Long> requestsLeftOnRemoteP, boolean btbHit) {
        long spin = 0;
        long ncs = 0;

        long zeta = 0;

        long n = 0;
        long indirect_spin = 0;
        long direct_spin = 0;

        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);// local优先级高于tau_i的任务
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                zeta += (long) (int) Math.ceil((double) (time + (btbHit ? Ris[hpTask.partition][i] : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }
        ncs = zeta; // 高关键任务的访问次数
        //N_i
        if (task.resource_required_index.contains(resource.id - 1))
            n += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));


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


                    if (number_of_request_by_Remote_P - ncs - n > 0)    // L做准备
                        requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs - n);
                }
            }
        }
        //<indirect,direct>
        ArrayList<Long> spin_all = new ArrayList<>();
        spin_all.add(indirect_spin);
        spin_all.add(direct_spin);

        return spin_all;
    }

    /*
     * Calculate the local high priority tasks' interference for a given task t.
     * CI is a set of computation time of local tasks, including spin delay.
     *
     * last part of the formula
     *
     */
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long time, long[][] Ris, ArrayList<Resource> resources) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET + hpTask.pure_resource_execution_time + CX2);
            }
        }
        return interference;
    }

    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
        ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, tasks.get(t.partition));
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;
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
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                    && resource.getCeilingForProcessor(localTasks) >= task.priority) {
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

}
