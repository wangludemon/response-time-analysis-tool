package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**  MSRP analysis from xxx
 *  Response time including:
 *  WCET
 *  Resource execution time
 *  interference: only preemption time
 *  spin Blocking: direct and indirect
 *  arrival blocking **/

public class MSRPNew {

    long count = 0;

    public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, boolean printDebug) {
        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);

        long[][] response_time = new long[tasks.size()][];
        count = 0;

        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }

        new AnalysisUtils().cloneList(init_Ri, response_time);

        /* a huge busy window to get a fixed Ri */
        boolean isEqual = false, missdeadline = false; //用以退出循环的标志
        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            boolean should_finish = true;
            // 核心迭代计算函数
            long[][] response_time_plus = busyWindow(tasks, resources, response_time, true);

            for (int i = 0; i < response_time_plus.length; i++) {
                for (int j = 0; j < response_time_plus[i].length; j++) {
                    if (response_time[i][j] != response_time_plus[i][j])
                        isEqual = false;

                }
            }
            count++;
            AnalysisUtils.cloneList(response_time_plus, response_time);

        }

        if (printDebug) {
            if (missdeadline)
                System.out.println("FIFONP JAVA    after " + count + " tims of recursion, the tasks miss the deadline.");
            else
                System.out.println("FIFONP JAVA    after " + count + " tims of recursion, we got the response time.");

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

                if (response_time[i][j] > task.deadline) {
                    response_time_plus[i][j] = response_time[i][j];
                    continue;
                }

                task.spin = directRemoteDelay(task, tasks, resources, response_time, response_time[i][j], btbHit) ;

                task.interference = highPriorityInterference(task, tasks, response_time[i][j], response_time, resources, btbHit);
                task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j], btbHit);

                response_time_plus[i][j] = task.Ri = task.WCET + task.spin + task.interference + task.local + task.pure_resource_execution_time;

                if (task.Ri > task.deadline) {
                    return response_time_plus;
                }
            }
        }
        return response_time_plus;
    }

    /*
     * Calculate the local high priority tasks' interference for a given task t.
     * CI is a set of computation time of local tasks, including spin delay.
     */
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long Ri, long[][] Ris, ArrayList<Resource> resources,
                                          boolean btbHit) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                // 抢占时间
                interference += Math.ceil((double) (Ri) / (double) hpTask.period) * (hpTask.WCET);

//                // 间接阻塞
//                long btb_interference = getIndirectSpinDelay(hpTask, Ri, Ris[partition][i], Ris, allTasks, resources, btbHit);
//
//                interference += btb_interference;
            }
        }

        return interference;
    }

    /*
     * for a high priority task hpTask, return its back to back hit time when
     * the given task is pending
     */
    private long getIndirectSpinDelay(SporadicTask hpTask, long Ri, long Rihp, long[][] Ris, ArrayList<ArrayList<SporadicTask>> allTasks,
                                      ArrayList<Resource> resources, boolean btbHit) {
        long BTBhit = 0;

        for (int i = 0; i < hpTask.resource_required_index.size(); i++) {
            /* for each resource that a high priority task request */
            Resource resource = resources.get(hpTask.resource_required_index.get(i));

            int number_of_higher_request = getNoRFromHP(resource, hpTask, allTasks.get(hpTask.partition), Ris[hpTask.partition], Ri, btbHit);
//			int number_of_request_with_btb = (int) Math.ceil((double) (Ri + (btbHit ? Rihp : 0)) / (double) hpTask.period)
//					* hpTask.number_of_access_in_one_release.get(i);
            int number_of_request_with_btb = (int) Math.ceil((double) (Ri) / (double) hpTask.period)
                    * hpTask.number_of_access_in_one_release.get(i);

            BTBhit += number_of_request_with_btb * resource.csl;

            for (int j = 0; j < resource.partitions.size(); j++) {
                if (resource.partitions.get(j) != hpTask.partition) {
                    int remote_partition = resource.partitions.get(j);
                    int number_of_remote_request = getNoRRemote(resource, allTasks.get(remote_partition), Ris[remote_partition], Ri, btbHit);

                    int possible_spin_delay = number_of_remote_request - number_of_higher_request < 0 ? 0 : number_of_remote_request - number_of_higher_request;

                    int spin_delay_with_btb = Integer.min(possible_spin_delay, number_of_request_with_btb);

                    BTBhit += spin_delay_with_btb * resource.csl;
                }
            }
        }
        return BTBhit;
    }

    /*
     * Calculate the spin delay for a given task t.
     */
    private long directRemoteDelay(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri,
                                   boolean btbHit) {
        long spin_delay = 0;
        // 直接自旋阻塞 只遍历了任务t
        for (int k = 0; k < t.resource_required_index.size(); k++) {
            Resource resource = resources.get(t.resource_required_index.get(k));
            spin_delay += getNoSpinDelay(t, resource, tasks, Ris, Ri, btbHit) * resource.csl;
        }

        for (int i = 0; i <tasks.get(t.partition).size(); i++) {
            if (tasks.get(t.partition).get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(t.partition).get(i);

                // 间接阻塞
                long btb_interference = getIndirectSpinDelay(hpTask, Ri, Ris[t.partition][i], Ris, tasks, resources, btbHit);

                spin_delay += btb_interference;
            }
        }


        return spin_delay;
    }

    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri, boolean btbHit) {
        ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, tasks);
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;

            if (res.isGlobal) {
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], Ri, btbHit);

                    if (partition != t.partition && (norHP + norT) < norR) {
                        local_blocking += res.csl;
                    }
                }
            }
            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> getLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> tasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                    && resource.getCeilingForProcessor(tasks.get(partition)) >= task.priority) {
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
     * gives the number of requests from remote partitions for a resource that
     * is required by the given task.
     */
    private int getNoSpinDelay(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris, long Ri, boolean btbHit) {
        int number_of_spin_dealy = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (i != task.partition) {
                /* For each remote partition */
                int number_of_request_by_Remote_P = 0;
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = tasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        int number_of_release = (int) Math.ceil((double) (Ri + (btbHit ? Ris[i][j] : 0)) / (double) remote_task.period);
                        number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                // 本地高优先级任务访问次数
                int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], Ri, btbHit);

                // 实际造成的阻塞
                int possible_spin_delay = number_of_request_by_Remote_P - getNoRFromHP < 0 ? 0 : number_of_request_by_Remote_P - getNoRFromHP;

                int NoRFromT = task.number_of_access_in_one_release.get(getIndexRInTask(task, resource));
                number_of_spin_dealy += Integer.min(possible_spin_delay, NoRFromT);
            }
        }
        return number_of_spin_dealy;
    }

    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit) {
        int number_of_request_by_Remote_P = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? Ris[i] : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
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
                number_of_request_by_HP += Math.ceil((double) (Ri) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(indexR);
//				number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? Ris[i] : 0)) / (double) hpTask.period)
//						* hpTask.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_HP;
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
