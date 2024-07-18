package com.demo.tool.responsetimeanalysis.analysis;

import  com.demo.tool.responsetimeanalysis.entity.Resource;
import  com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import  com.demo.tool.responsetimeanalysis.generator.PriorityGenerator;
import  com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import com.demo.tool.responsetimeanalysis.utils.PairComparator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * version 1
 **/
public class DynamicAnalysisForModeSwitch {
    public long[][] getResponseTimeByDMPO(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                          int extendCal, boolean testSchedulability,
                                          boolean btbHit, boolean useRi, boolean useDM, boolean printDebug) {
        if (tasks == null)
            return null;

        // 在此处赋予优先级
        if (useDM) {
            // assign priorities by Deadline Monotonic
            tasks = new PriorityGenerator().assignPrioritiesByDM(tasks);
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).sort((t1, t2) -> -Integer.compare(t1.priority, t2.priority));
            }
        }

        long count = 0; // The number of calculations
        long np = 0; // The NP section length if MrsP is applied

        // 所有使用MrsP协议的资源中最大的csl作为npsection的值
        // high任务：csl=csl_high  low任务为 csl_low


        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);
        long[][] response_time = new long[tasks.size()][];
        boolean isEqual = false, missdeadline = false;
        count = 0;

        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }

        new AnalysisUtils().cloneList(init_Ri, response_time);

        ArrayList<ArrayList<ArrayList<Long>>> history = new ArrayList<>();

        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            boolean should_finish = true;
            long[][] response_time_plus = busyWindow(tasks, resources, lowTasks, response_time, AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION, np, extendCal,
                    testSchedulability, btbHit, useRi);


            for (int i = 0; i < response_time_plus.length; i++) {
                for (int j = 0; j < response_time_plus[i].length; j++) {
                    if (response_time[i][j] != response_time_plus[i][j])
                        isEqual = false;
                    if (testSchedulability) {
                        if (response_time_plus[i][j] > tasks.get(i).get(j).deadline)
                            missdeadline = true;
                    } else {
                        if (response_time_plus[i][j] <= tasks.get(i).get(j).deadline * extendCal)
                            should_finish = false;
                    }
                }
            }

            count++;
            new AnalysisUtils().cloneList(response_time_plus, response_time);

            if (testSchedulability) {
                if (missdeadline)
                    break;
            } else {
                if (should_finish)
                    break;
            }
        }

        if (printDebug) {
            System.out.println("FIFO Spin Locks Framework    after " + count + " tims of recursion, we got the response time.");
            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                long[][] response_time, double oneMig, long np,
                                int extendCal, boolean testSchedulability, boolean btbHit, boolean useRi) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {

                SporadicTask task = tasks.get(i).get(j);
//                if (response_time[i][j] > task.deadline * extendCal) {
//                    response_time_plus[i][j] = response_time[i][j];
//                    continue;
//                }

                response_time_plus[i][j] = oneCalculation(task, tasks, resources, lowTasks, response_time, response_time[i][j], oneMig, np, btbHit, useRi);

                if (testSchedulability && task.Ri > task.deadline) {
                    return response_time_plus;
                }
            }
        }
        return response_time_plus;
    }

    private long oneCalculation(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                long[][] response_time, long Ri,
                                double oneMig, long np, boolean btbHit, boolean useRi) {

        task.Ri = task.spin = task.interference = task.local = task.indirect_spin = task.total_blocking = 0;
        task.np_section = task.blocking_overheads = task.implementation_overheads = task.migration_overheads_plus = 0;
        task.mrsp_arrivalblocking_overheads = task.fifonp_arrivalblocking_overheads = task.fifop_arrivalblocking_overheads = 0;
        task.test_delay = 0;

        task.implementation_overheads += AnalysisUtils.FULL_CONTEXT_SWTICH1;

        task.spin = FIFOPResourceAccessTime(task, tasks, resources, lowTasks, response_time, Ri, btbHit, useRi);
        task.interference = highPriorityInterference(task, tasks, resources, lowTasks, response_time, Ri, oneMig, np, btbHit, useRi);
        task.local = localBlocking(task, tasks, resources, lowTasks, response_time, Ri, oneMig, np, btbHit, useRi);

        long implementation_overheads = (long) Math.ceil(task.implementation_overheads + task.migration_overheads_plus);
        long newRi = task.Ri = task.WCET + task.spin + task.interference + task.local + implementation_overheads;

        task.total_blocking = task.spin + task.indirect_spin + task.local - task.pure_resource_execution_time + (long) Math.ceil(task.blocking_overheads);
        if (task.total_blocking < 0) {
            System.err.println("total blocking error: T" + task.id + "   total blocking: " + task.total_blocking);
            System.exit(-1);
        }

        return newRi;
    }

    /***************************************************
     ************* Direct Spin Delay *******************
     ***************************************************/

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

    /*
     * gives that number of requests from HP local tasks for a resource that is
     * required by the given task.
     */
    private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit, boolean useRi, boolean useRiLow) {
        int number_of_request_by_HP = 0;
        int priority = task.priority;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask hpTask = tasks.get(i);
                int indexR = getIndexRInTask(hpTask, resource);
                if (useRiLow)
                    number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? (useRi ? hpTask.Ri_LO : hpTask.deadline) : 0)) / (double) hpTask.period)
                            * hpTask.number_of_access_in_one_release.get(indexR);
                else
                    number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                            * hpTask.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_HP;
    }

    /**
     * FIFO-P resource accessing time.
     */
    private long FIFOPResourceAccessTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                         long[][] Ris, long time,
                                         boolean btbHit, boolean useRi) {
        long spin = 0;
        //ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
        // 1. requestsLeftOnRemoteP(RBTQ),在考虑Direct/Indirect Spin Delay后,对于每个资源（一维）各处理器（二维）剩余的资源访问次数(三维、区分clo和chi)；
        ArrayList<ArrayList<ArrayList<Long>>> requestsLeftOnRemoteP = new ArrayList<>();

        for (Resource res : resources) {
            // 每个资源都新建一个list以存储各remote processor的'剩余'次数；
            requestsLeftOnRemoteP.add(new ArrayList<ArrayList<Long>>());
            // 计算各资源产生的Direct/Indirect Spin Delay，将最新加入的'requestsLeftOnRemoteP中的list'传入，在函数内部更新；
            spin += getSpinDelayForOneResoruce(task, tasks, res, lowTasks, requestsLeftOnRemoteP.get(requestsLeftOnRemoteP.size() - 1), Ris, time, btbHit, useRi);
        }

        // 计算重试成本
        // preemptions
        long preemptions = 0;
        long sum_preemptions = 0;

        // 进一步地，需要计算取消机制带来额外的阻塞（访问资源时的抢占引发）
        // 1. 建立本地高优先级任务的priority-preemptions对儿，在后续将根据抢占的'priority'判断其是否能引发抢占。mode switch分别考虑双关键级任务
        Map<Integer, Long> pp = new HashMap<Integer, Long>();
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            int high_task_priority = tasks.get(task.partition).get(i).priority;
            if (high_task_priority > task.priority) {
                // 在这个高优先级任务的'优先级'下，high任务抢占次数
                preemptions = (int) Math.ceil((time) / (double) tasks.get(task.partition).get(i).period);
                sum_preemptions += preemptions;
                pp.put(high_task_priority, preemptions);
            }
        }
        for (int i = 0; i < lowTasks.get(task.partition).size(); i++) {
            int high_task_priority = lowTasks.get(task.partition).get(i).priority;
            if (high_task_priority > task.priority) {
                // 在这个高优先级任务的'优先级'下，low任务抢占次数(time_window=Ri_LO)
                preemptions = (int) Math.ceil((task.Ri_LO) / (double) lowTasks.get(task.partition).get(i).period);
                sum_preemptions += preemptions;
                pp.put(high_task_priority, preemptions);
            }
        }

        task.implementation_overheads += sum_preemptions * (AnalysisUtils.FIFOP_CANCEL);
        task.blocking_overheads += sum_preemptions * (AnalysisUtils.FIFOP_CANCEL);
        //key 降序
        Map<Integer, Long> sortedMap = pp.entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        // 新算法开始
        // 1. 需要计算访问各requestsLeftOnRemoteP的csl-Queue；
        ArrayList<ArrayList<Pair<Long,Integer>>> all_csl_queue = new ArrayList<>();

        //requestsLeftOnRemoteP.size() == number of resource
        for (int p = 0; p < requestsLeftOnRemoteP.size(); p++) {
            // one resource blocking time 获取一个资源对应的每个处理器上的阻塞次数队列rlorp
            ArrayList<ArrayList<Long>> rlorp = new ArrayList<ArrayList<Long>>(requestsLeftOnRemoteP.get(p));
            // 计算每取一次的最大远程阻塞时间
            //ArrayList<Long> csl_queue = new ArrayList<>();
            ArrayList<Pair<Long, Integer>> csl_queue = new ArrayList<>();
            while (rlorp.size() != 0) {

                long csl_sum = 0;
                int cnt = 0;
                for (int z = 0; z < rlorp.size(); z++) {
                    // 第z个远程处理器的RBTQ第一项
                    csl_sum += rlorp.get(z).remove(0);
                    cnt+=1;
                    if (rlorp.get(z).size() == 0)
                        rlorp.remove(z);
                    z--;
                }
                csl_queue.add(new Pair<>(csl_sum, cnt));

            }
            all_csl_queue.add(csl_queue); //size() == number of resource
        }

        Map<Set<Integer>, Long> www = new HashMap<Set<Integer>, Long>();
        for (Integer high_task_priority : sortedMap.keySet()) {
            long preempt = sortedMap.get(high_task_priority);

            Set<Integer> rIndexList = new HashSet<>();
            for (int w = 0; w < task.resource_required_index.size(); w++) {
                int rIndex = task.resource_required_index.get(w);
                if (high_task_priority > task.resource_required_priority.get(w)) {
                    rIndexList.add(rIndex);
                }
            }
            // 任务\tau_x的高优先级任务
            ArrayList<SporadicTask> taskset = tasks.get(task.partition);

            //taskset.addAll(lowTasks.get(task.partition));   // lowTask

            for (int c = 0; c < taskset.size(); c++) {
                SporadicTask high_task = taskset.get(c);
                if (high_task.priority > task.priority) {
                    for (int w = 0; w < high_task.resource_required_index.size(); w++) {
                        int rIndex = high_task.resource_required_index.get(w);
                        if (high_task_priority > high_task.resource_required_priority.get(w)) {
                            rIndexList.add(rIndex);
                        }
                    }
                }
            }

            taskset = lowTasks.get(task.partition);
            for (int c = 0; c < taskset.size(); c++) {
                SporadicTask high_task = taskset.get(c);
                if (high_task.priority > task.priority) {
                    for (int w = 0; w < high_task.resource_required_index.size(); w++) {
                        int rIndex = high_task.resource_required_index.get(w);
                        if (high_task_priority > high_task.resource_required_priority.get(w)) {
                            rIndexList.add(rIndex);
                        }
                    }
                }
            }
            if (rIndexList.size() != 0) {
                www.put(rIndexList, www.getOrDefault(rIndexList, (long) 0) + preempt);
            }
        }
        Map<Set<Integer>, Long> sortedwww = www.entrySet()
                .stream()
                .sorted(Map.Entry.<Set<Integer>, Long>comparingByKey(Comparator.comparingInt(set -> set.size())).reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
        ArrayList<Pair<Long, Integer>> sum_array = new ArrayList<>();
        if (sortedwww.size() > 0) {
            List<Map.Entry<Set<Integer>, Long>> entryList = new ArrayList<>(sortedwww.entrySet());
            Long sum_preempt = 0L;
            // 迭代过程优化
            for (int i = 0; i < entryList.size(); i++) {
                Map.Entry<Set<Integer>, Long> A = entryList.get(i);
                Map.Entry<Set<Integer>, Long> B = i == entryList.size() - 1 ? null : entryList.get(i + 1);
                Set<Integer> A_list = A.getKey();
                Set<Integer> B_list = B == null ? new HashSet<>() : B.getKey();
                Long A_preempt = A.getValue();
                sum_preempt += A_preempt;
                // 创建一个新的集合来存储差集，以便保持原始集合不变
                Set<Integer> differenceSet = new HashSet<>(A_list);
                // 从differenceSet中移除setA中的所有元素，得到差集
                differenceSet.removeAll(B_list);
                // 从differnceSet中索引指示的all_csl_queue中的几个queue合并，并取前sum_preempt个最大的数存入sum_array中
                ArrayList<Pair<Long, Integer>> sum_list = new ArrayList<>();
                for (Integer index : differenceSet) {
                    sum_list.addAll(all_csl_queue.get(index));
                }
                // sum_array和sum_list合并
                sum_array.addAll(sum_list);
                // 从大到小排序
                //sum_array.sort(Comparator.reverseOrder());
                if(sum_array.size() > 1 && sum_array.get(0) != null){
                    // 使用Collections.sort方法和自定义比较器来进行降序排序
                    Collections.sort(sum_array, new PairComparator<Long, Integer>());
                }
                sum_array = new ArrayList<>(sum_array.subList(0, (int)Math.min(sum_array.size(),sum_preempt)));
            }


        }


        if(sum_array.size() > 0){
            for(Pair<Long, Integer> pair : sum_array) {
                spin += pair.getFirst();
                task.implementation_overheads += pair.getSecond() * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
                task.blocking_overheads += pair.getSecond() * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
            }
            // overhead需要根据'实际造成的抢占'加入
            task.implementation_overheads += sum_array.size() * (AnalysisUtils.FIFOP_CANCEL);
            task.blocking_overheads += sum_array.size() * (AnalysisUtils.FIFOP_CANCEL);
        }

        return spin;
    }

    // 返回任务因为某个资源访问的总耗时，包含自身访问时间、高优先级任务访问时间、直接和间接spin等
    private long getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                            ArrayList<ArrayList<Long>> requestsLeftOnRemoteP, long[][] Ris, long time, boolean btbHit, boolean useRi) {
        long spin = 0;
        long nspin = 0;
        long ncs = 0;
        long ncs_lo = 0;
        long ncs_hi = 0;
        long N_i_k = 0;

        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + (btbHit ? (useRi ? hpTask.Ri_LO : hpTask.deadline) : 0)) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                ncs_hi += (long) Math.ceil((double) (time + (btbHit ? (useRi ? Ris[task.partition][i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }
        // 任务自身访问资源的次数
        if (task.resource_required_index.contains(resource.id - 1))
            N_i_k += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));

        ncs = ncs_hi + ncs_lo + N_i_k;

        // remote task
        if (ncs > 0) {
            for (int i = 0; i < tasks.size(); i++) {
                if (task.partition != i) {
                    /* For each remote partition */
                    long number_of_low_request_by_Remote_P = 0;
                    long number_of_high_request_by_Remote_P = 0;
                    // 远程处理器HI任务访问资源次数
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            int number_of_release = (int) Math
                                    .ceil((double) (time + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                            number_of_high_request_by_Remote_P += (long) number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }
                    // 远程处理器LO任务访问资源次数
                    for (int j = 0; j < LowTasks.get(i).size(); j++) {
                        if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = LowTasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + (btbHit ? (useRi ? remote_task.Ri_LO : remote_task.deadline) : 0)) / (double) remote_task.period);
                            number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }

                    long possible_spin_delay = Long.min(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P, ncs);
                    // 建立RBTQ
                    ArrayList<Long> RBTQ = new ArrayList<>();
                    while (number_of_high_request_by_Remote_P > 0) {
                        RBTQ.add(resource.csl_high);
                        number_of_high_request_by_Remote_P--;
                    }
                    //min{local, remote m}
                    while (number_of_low_request_by_Remote_P > 0) {
                        RBTQ.add(resource.csl_low);
                        number_of_low_request_by_Remote_P--;
                    }


                    while (possible_spin_delay > 0) {
                        spin += RBTQ.remove(0);
                        possible_spin_delay--;
                    }
                    // 这个处理器还剩余次数的话加入
                    if (RBTQ.size() > 0)
                        requestsLeftOnRemoteP.add(RBTQ);

                    nspin += possible_spin_delay;
                    // long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);
                    // spin += possible_spin_delay;
                    // if (number_of_request_by_Remote_P - ncs > 0)
                    //    requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
                }
            }
        }

        task.implementation_overheads += (nspin + ncs) * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
        task.blocking_overheads += (nspin + ncs
                - (task.resource_required_index.contains(resource.id - 1)
                ? task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1))
                : 0))
                * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
        // 最终算式 远程spin + 高优先级LO访问 + 高优先级HI访问 + 自身访问
        return spin + ncs_lo * resource.csl_low + ncs_hi * resource.csl_high + N_i_k * resource.csl_high;
        //return spin * resource.csl + ncs * resource.csl;
    }




    // return the totol number of request on remote processor
    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, ArrayList<SporadicTask> LowTasks,
                             long[] Ris, long Ri, boolean btbHit, boolean useRi) {
        int number_of_request_by_Remote_P = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : remote_task.deadline) : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        for (int i = 0; i < LowTasks.size(); i++) {
            if (LowTasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = LowTasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? (useRi ? remote_task.Ri_LO : remote_task.deadline) : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_Remote_P;
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



    /***************************************************
     ************* InDirect Spin Delay *******************
     ***************************************************/
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources,
                                          ArrayList<ArrayList<SporadicTask>> LowTasks,
                                          long[][] Ris, long time,
                                          double oneMig, long np, boolean btbHit, boolean useRi) {
        long interference = 0;
        int partition = t.partition;
        // 高关键任务
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
                t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);
            }
        }
        // 低关键任务
        ArrayList<SporadicTask> lowTasks = LowTasks.get(partition);

        for (int i = 0; i < lowTasks.size(); i++) {
            if (lowTasks.get(i).priority > t.priority) {
                SporadicTask hpTask = lowTasks.get(i);
                interference += Math.ceil((double) (t.Ri_LO) / (double) hpTask.period) * (hpTask.WCET);
                t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);
            }
        }


        return interference;
    }


    /***************************************************
     ************** Arrival Blocking *******************
     ***************************************************/
    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                               long[][] Ris, long time, double oneMig,
                               long np, boolean btbHit, boolean useRi) {
        long fifop_localblocking = FIFOPlocalBlocking(t, tasks, resources, LowTasks, Ris, time, btbHit, useRi);
        t.implementation_overheads += t.fifop_arrivalblocking_overheads;
        t.blocking_overheads += t.fifonp_arrivalblocking_overheads;

        //long npsection = (isTaskIncurNPSection(t, localTasks, resources) ? np : 0);

        return fifop_localblocking;
    }



    private long FIFOPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                    long[][] Ris, long Ri,
                                    boolean btbHit, boolean useRi) {
        ArrayList<ArrayList<Resource>> LocalBlockingResources = FIFOPgetLocalBlockingResources_Dynamic(t, resources, tasks.get(t.partition));
        // (Short) 1. 本地资源（即使是升到Ceiling） + 只有'执行'时才会优先级反转的资源，它们只能给task造成一个csl的Arrival Blocking；
        // (Long) 2. 等待这些资源时也会发生优先级反转的资源，它们会对task造成更多的Arrival Blocking；
        ArrayList<Resource> shortLocalBlockingResource = LocalBlockingResources.get(0);
        ArrayList<Resource> longLocalBlockingResource = LocalBlockingResources.get(1);

        //pair<阻塞时间csl, 阻塞次数（用于计算lock开销）>
        ArrayList<Pair<Long, Integer>> local_blocking_each_resource = new ArrayList<>();

        //区分被高关键任务访问的资源和只被低关键任务访问的资源，默认csl_hi>csl_lo
        ArrayList<SporadicTask> localTask = tasks.get(t.partition);
        Set<Integer> res_HI_index = new HashSet<>();;
        for (int i=0;i<localTask.size();i++){
            SporadicTask task = localTask.get(i);
            for (int j=0;j<task.resource_required_index.size();j++){
                int index = task.resource_required_index.get(j);
                res_HI_index.add(index);
            }
        }
        //task可抢占，最多一个csl
        for (Resource res : shortLocalBlockingResource) {
            long local_blocking = 0;
            if (res_HI_index.contains(res.id))
                local_blocking = res.csl_high;
            else
                local_blocking = res.csl_low;
            local_blocking_each_resource.add(new Pair<>(local_blocking, 1));
        }

        for (Resource res : longLocalBlockingResource) {
            long local_blocking = 0;
            int blocking_time = 1;
            if (res_HI_index.contains(res.id))
                local_blocking = res.csl_high;
            else
                local_blocking = res.csl_low;

            local_blocking += res.csl_high * (res.partitions.size()-1);
            blocking_time = res.partitions.size();
//            for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
//                int partition = res.partitions.get(parition_index);
//                //高优先级任务请求次数
//                int norHP_HI = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi, false);
//                int norHP_LO = getNoRFromHP(res, t, LowTasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi, true);
//                int norHP = norHP_HI + norHP_LO;
//                //任务t访问次数
//                int norT = t.resource_required_index.contains(res.id - 1)
//                        ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
//                        : 0;
//                //远程核心访问次数
//                int norR_HI = getNoRRemote(res, tasks.get(partition), null, Ris[partition], Ri, btbHit, useRi);
//                int norR_LO = getNoRRemote(res, null, LowTasks.get(t.partition), Ris[partition], Ri, btbHit, useRi);
//
//                if (partition != t.partition) {
//                    if ( (norHP + norT) < norR_HI ){
//                        local_blocking += res.csl_high;
//                        blocking_time+=1;
//                    }
//                    else if ( (norHP + norT) < norR_HI+norR_LO ){
//                        local_blocking += res.csl_low;
//                        blocking_time+=1;
//                    }
//
//                }
//            }
            local_blocking_each_resource.add(new Pair<>(local_blocking, blocking_time));
        }



        if (local_blocking_each_resource.size() > 1)
            Collections.sort(local_blocking_each_resource, new PairComparator<Long, Integer>());


        if (local_blocking_each_resource.size() > 0)
            t.fifop_arrivalblocking_overheads = (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK)
                    * local_blocking_each_resource.get(0).getSecond();

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0).getFirst() : 0;
    }

    private ArrayList<ArrayList<Resource>> FIFOPgetLocalBlockingResources_Dynamic(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {

        // 这里，其实我们要返回的是两个资源的List:
        // (Short) 1. 本地资源（即使是升到Ceiling） + 只有'执行'时才会优先级反转的资源，它们只能给task造成一个csl的Arrival Blocking；
        // (Long) 2. 等待这些资源时也会发生优先级反转的资源，它们会对task造成更多的Arrival Blocking；
        // 当然，为考虑最坏情况，对于以上两种类型的资源，我们仍将找出的是可能造成最大阻塞的一个资源。
        ArrayList<ArrayList<Resource>> localBlockingResources = new ArrayList<>();
        ArrayList<Resource> shortLocalBlockingResources = new ArrayList<>();
        ArrayList<Resource> longLocalBlockingResources = new ArrayList<>();

        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling  ###本地资源
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                    && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        shortLocalBlockingResources.add(resource);
                        break;
                    }
                }
            }
            // global resources that are accessed from the partition ###全局资源
            if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        int rIndex = LP_task.resource_required_index.indexOf(resource.id - 1);
                        int pri = LP_task.resource_required_priority.get(rIndex);
                        // 如果小于，实际上只能造成一个csl的arrival blocking；
                        if (pri < task.priority){
                            shortLocalBlockingResources.add(resource);
                            break;
                        }
                        // 如果大于等于，那么就加到long中
                        else{
                            longLocalBlockingResources.add(resource);
                            break;
                        }
                    }
                }
            }
        }
        localBlockingResources.add(shortLocalBlockingResources);
        localBlockingResources.add(longLocalBlockingResources);
        return localBlockingResources;
    }


    private int getMaxPriorityOfLocalTasks(ArrayList<SporadicTask> tasks){
        int max_priority = -1;
        for(SporadicTask t: tasks){
            if(t.priority > max_priority){
                max_priority = t.priority;
            }
        }
        return max_priority;
    }


}