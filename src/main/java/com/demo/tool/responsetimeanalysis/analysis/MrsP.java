package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class MrsP {
    public static Logger log = LogManager.getLogger();
    long count = 0;
    long overhead = (long) (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
    long CX1 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH1;
    long CX2 = (long) AnalysisUtils.FULL_CONTEXT_SWTICH2;

    public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, boolean printBebug) {
        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);

        long[][] response_time = new long[tasks.size()][];
        boolean isEqual = false, missDeadline = false;
        count = 0;

        for (int i = 0; i < init_Ri.length; i++)
            response_time[i] = new long[init_Ri[i].length];

        new AnalysisUtils().cloneList(init_Ri, response_time);

        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            long[][] response_time_plus = busyWindow(tasks, resources, response_time);

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

        if (printBebug) {
            if (missDeadline)
                System.out.println("OriginalMrsPRTA    after " + count + " tims of recursion, the tasks miss the deadline.");
            else
                System.out.println("OriginalMrsPRTA    after " + count + " tims of recursion, we got the response time.");

            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time) {
        long[][] response_time_plus = new long[tasks.size()][];
        for (int i = 0; i < response_time.length; i++)
            response_time_plus[i] = new long[response_time[i].length];

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);

                task.spin = resourceAccessingTime(task, resources);
                task.interference = highPriorityInterference(task, tasks, response_time[i][j]);
                task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j]);
                task.indirect_spin = highPriorityIndirectSpin(task, tasks, response_time[i][j]);

                response_time_plus[i][j] = task.Ri = task.WCET + task.spin + task.interference + task.local + CX1;
                if (task.Ri > task.deadline)
                    return response_time_plus;
            }
        }
        return response_time_plus;

    }

    /*
     * Calculate the local high priority tasks' interference for a given task t.
     * CI is a set of computation time of local tasks, including spin delay.
     */
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long Ri) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (Ri) / (double) hpTask.period) * (hpTask.WCET + hpTask.spin + CX2);
            }
        }
        return interference;
    }

    /*
     *
     * Calculate the indirect spin
     *
     */
    private long highPriorityIndirectSpin(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long Ri) {
        long indirect_spin = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                indirect_spin += Math.ceil((double) (Ri) / (double) hpTask.period) * hpTask.spin;
            }
        }
        return indirect_spin;
    }

    /*
     * Calculate the spin delay for a given task t.
     */
    private long resourceAccessingTime(SporadicTask t, ArrayList<Resource> resources) {
        long spin_delay = 0;
        for (int k = 0; k < t.resource_required_index.size(); k++) {
            Resource resource = resources.get(t.resource_required_index.get(k));
            spin_delay += resource.partitions.size() * (resource.csl + overhead) * t.number_of_access_in_one_release.get(k);
        }
        return spin_delay;
    }

    /*
     * Calculate the local blocking for task t.
     */
    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
        ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, tasks.get(t.partition));
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl * res.partitions.size();
            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;

    }

    /*
     * gives a set of resources that can cause local blocking for a given task
     */
    private ArrayList<Resource> getLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            if (resource.partitions.contains(partition) && resource.getCeilingForProcessor(localTasks) >= task.priority) {
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
}
