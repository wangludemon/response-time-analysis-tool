package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**  MSRP analysis for mode switch from MSRP-FT: Reliable Resource Sharing on Multiprocessor Mixed-Criticality Systems
 *  Response time including:
 *  WCET
 *  Resource execution time
 *  interference: WCET + Resource execution time from high priority tasks  (LO task during R_LO )
 *  spin Blocking: direct and indirect spin blocking
 *  arrival blocking **/

public class MSRPNewForModeSwitch {
    public static Logger log = LogManager.getLogger();
    long count = 0;

    ArrayList<ArrayList<ArrayList<Long>>> requestsLeftOnRemoteP;


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

                // 只有spin delay 不计算resource execution
                task.spin = getSpinDelay(task, tasks, lowTasks, resources, response_time[i][j], response_time);
                task.interference = highPriorityInterference(task, tasks, lowTasks, response_time[i][j]);
                task.local = localBlocking(task, tasks,  resources, lowTasks);

                response_time_plus[i][j] = task.Ri = task.WCET + task.spin  + task.interference + task.local + task.prec_HIGH;

                if (task.Ri > task.deadline)
                    return response_time_plus;

            }
        }
        return response_time_plus;
    }


    /*
     * Calculate the spin delay for a given task t.
     */
    private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris) {
        long spin = 0;

        // 资源， 处理器， 处理器内的csl
        this.requestsLeftOnRemoteP = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            requestsLeftOnRemoteP.add(new ArrayList<>());
            Resource res = resources.get(i);
            // 只计算了远程阻塞
            spin += getSpinDelayForOneResource(task, tasks, LowTasks, res, time, Ris, requestsLeftOnRemoteP.get(i));

        }

        return spin;
    }

    private Long getSpinDelayForOneResource(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris,
                                                       ArrayList<ArrayList<Long>> requestsLeftOnRemoteP) {
        long spin = 0;
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
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_h_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_hi += (long) Math.ceil((double) (time) / (double) hpTask.period) * n_h_k;
            }
        }

        ncs = N_i_k + ncs_lo + ncs_hi;

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

                long possible_spin_delay = Long.min(ncs, number_of_high_request_by_Remote_P+number_of_low_request_by_Remote_P);

                // 建立RBTQ
                ArrayList<Long> RBTQ = new ArrayList<>();
                while (number_of_high_request_by_Remote_P > 0) {
                    RBTQ.add(resource.csl_high );
                    number_of_high_request_by_Remote_P--;
                }
                //min{local, remote m}
                while (number_of_low_request_by_Remote_P > 0) {
                    RBTQ.add(resource.csl_low );
                    number_of_low_request_by_Remote_P--;
                }

                while (possible_spin_delay > 0) {
                    spin += RBTQ.get(0);
                    RBTQ.remove(0);
                    possible_spin_delay--;
                }
                // 这个处理器还剩余次数的话加入
                if (RBTQ.size() > 0)
                    requestsLeftOnRemoteP.add(RBTQ);
            }
        }


        return spin;
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
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET + hpTask.prec_HIGH);
            }
        }
        // LO Task 部分
        for (SporadicTask hpTask : LowTasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (t.Ri_LO) / (double) hpTask.period) * (hpTask.WCET + hpTask.prec_LOW);
            }
        }
        return interference;
    }

    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources,  ArrayList<ArrayList<SporadicTask>> lowtasks) {
        ArrayList<ArrayList<SporadicTask>> allTasks = new ArrayList<>();
        for (int i = 0; i < tasks.size();i++){
            ArrayList<SporadicTask> temp = new ArrayList<>();
            temp.addAll(tasks.get(i));
            temp.addAll(lowtasks.get(i));
            allTasks.add(temp);
        }

        ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, allTasks);
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;

            if (res.isGlobal) {
                // 计算远程阻塞
                ArrayList<ArrayList<Long>> RBTQ_P = requestsLeftOnRemoteP.get(res.id-1);
                for (ArrayList<Long> RBTQ : RBTQ_P){
                    local_blocking += RBTQ.get(0);
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




    private long localBlocking1(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> lowtasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
        int partition = t.partition;
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();
        Set<Resource> localResource = new HashSet<>();
        Set<Resource> globalResource = new HashSet<>();

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                    && resource.getCeilingForProcessor(tasks.get(partition)) >= t.priority) {

                for (int j = 0; j < resource.requested_tasks.size(); j++) {  /**需要检查该数组requested_tasks  初始包括了lo和hi， 但是高关键模式需要去掉低关键任务  **/
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < t.priority) {
                        // 本地资源必然只有一个小c
                        localResource.add(resource);
                        if (LP_task.critical == 1)  resource.onlyLow = false;
                    }
                }
            }
            // global resources that are accessed from the partition
            if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < t.priority) {
                        globalResource.add(resource);
                        if (LP_task.critical == 1)  resource.onlyLow = false;
                    }
                }
            }

        }

        for (Resource resource: localResource){
            if (resource.onlyLow)
                local_blocking_each_resource.add(resource.csl_low);
            else
                local_blocking_each_resource.add(resource.csl_high);
        }

        for (Resource resource: globalResource){

            // 计算远程阻塞
            ArrayList<ArrayList<Long>> RBTQ_P = requestsLeftOnRemoteP.get(resource.id-1);
            long remote_blocking = 0;
            for (ArrayList<Long> RBTQ : RBTQ_P){
                remote_blocking += RBTQ.get(0);
            }
            if (resource.onlyLow)
                local_blocking_each_resource.add(resource.csl_low+remote_blocking);
            else
                local_blocking_each_resource.add(resource.csl_high+remote_blocking);
        }


        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Long.compare(l1, l2));

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
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
                    RBTQ.add((resource.csl_high ));
                    number_of_high_request_by_Remote_P--;
                }
                //min{local, remote m}
                while (number_of_low_request_by_Remote_P > 0) {
                    RBTQ.add((resource.csl_low ));
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
