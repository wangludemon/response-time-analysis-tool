package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import com.demo.tool.responsetimeanalysis.utils.PairComparator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * version 1
 **/
public class DynamicAnalysis {

    /**
     * for stable mode
     **/
    // random the protocol for each resource -> update resource request priority
    public long[][] getResponseTimeByDMPO(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, int extendCal, boolean testSchedulability,
                                          boolean btbHit, boolean useRi, boolean useDM, boolean printDebug) {
        if (tasks == null) return null;

        // 在此处赋予优先级，已经由前端使用DMPO，否则就是下面这个
        if (!useDM) for (ArrayList<SporadicTask> task : tasks)
            task.sort((t1, t2) -> -Integer.compare(t1.priority, t2.priority));

        long count = 0; // The number of calculations
        long np = 0; // The NP section length if MrsP is applied

        // 所有使用MrsP协议的资源中最大的csl作为npsection的值
//        long npsection = 0;
//        for (int i = 0; i < resources.size(); i++) {
//            Resource resource = resources.get(i);
//            if ((resource.protocol == 6 || resource.protocol == 7) && npsection < resource.csl)
//                npsection = resources.get(i).csl;
//        }
//        np = npsection;

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
            long[][] response_time_plus = busyWindow(tasks, resources, response_time, AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION, np, extendCal,
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

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, double oneMig, long np,
                                int extendCal, boolean testSchedulability, boolean btbHit, boolean useRi) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);
                if (response_time[i][j] > task.deadline * extendCal) {
                    response_time_plus[i][j] = response_time[i][j];
                    continue;
                }

                response_time_plus[i][j] = oneCalculation(task, tasks, resources, response_time, response_time[i][j], oneMig, np, btbHit, useRi);

                if (testSchedulability && task.Ri > task.deadline) {
                    return response_time_plus;
                }
            }
        }
        return response_time_plus;
    }

    private long oneCalculation(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, long Ri,
                                double oneMig, long np, boolean btbHit, boolean useRi) {

        task.Ri = task.spin = task.interference = task.local = task.indirect_spin = task.total_blocking = 0;
        task.np_section = task.blocking_overheads = task.implementation_overheads = task.migration_overheads_plus = 0;
        task.mrsp_arrivalblocking_overheads = task.fifonp_arrivalblocking_overheads = task.fifop_arrivalblocking_overheads = 0;
        task.test_delay = 0;

        task.implementation_overheads += AnalysisUtils.FULL_CONTEXT_SWTICH1;    //所有实现开销 包括switch、lock、unclock，其中也涵盖了blocking_overhead
        task.spin = FIFOPResourceAccessTime(task, tasks, resources, response_time, Ri, btbHit, useRi);  //访问资源的时间和阻塞时间
        task.interference = highPriorityInterference(task, tasks, resources, response_time, Ri, oneMig, np, btbHit, useRi);
        task.local = localBlocking(task, tasks, resources, response_time, Ri, oneMig, np, btbHit, useRi);

        long implementation_overheads = (long) Math.ceil(task.implementation_overheads);    //此项需检查正确性
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


    /**
     * FIFO-P resource accessing time.
     */
    private long FIFOPResourceAccessTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                         boolean btbHit, boolean useRi) {
        // 用于计算资源相关的访问与阻塞时间项
        long spin = 0;
        // 1. requestsLeftOnRemoteP代表在考虑Direct/Indirect Spin Delay后，对于每个资源（一维）各处理器剩余的资源访问次数（二维）；
        ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();

        for (Resource res : resources) {
            // 每个资源都新建一个list以存储各remote processor的'剩余'次数；
            requestsLeftOnRemoteP.add(new ArrayList<>());
            // 计算各资源产生的Direct/Indirect Spin Delay，将最新加入的'requestsLeftOnRemoteP中的list'传入，在函数内部更新；
            spin += getSpinDelayForOneResoruce(task, tasks, res, requestsLeftOnRemoteP.get(requestsLeftOnRemoteP.size() - 1), Ris, time, btbHit, useRi);
        }

        // 进一步地，需要计算取消机制带来额外的阻塞（访问资源时的抢占引发）
        // 1. 建立本地高优先级任务的priority-preemptions对儿，在后续将根据抢占的'priority'判断其是否能引发抢占。
        Map<Integer, Long> pp = new HashMap<>();
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            int high_task_priority = tasks.get(task.partition).get(i).priority;
            if (high_task_priority > task.priority) {
                // 在这个高优先级任务的'优先级'下，抢占次数
                long preemptions = (int) Math.ceil((time) / (double) tasks.get(task.partition).get(i).period);
                pp.put(high_task_priority,preemptions);
            }
        }

        Map<Integer, Long> sortedMap = pp.entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        // 算法开始
        // 1. 需要计算访问各requestsLeftOnRemoteP的csl-Queue；
        //ArrayList<ArrayList<Long>> all_csl_queue = new ArrayList<>();
        ArrayList<ArrayList<Pair<Long,Integer>>> all_csl_queue = new ArrayList<>();

        for(int p = 0 ; p < requestsLeftOnRemoteP.size(); p++){
            ArrayList<Long> rlorp = new ArrayList<>(requestsLeftOnRemoteP.get(p));
            //pair<阻塞时间，阻塞次数>
            ArrayList<Pair<Long, Integer>> csl_queue = new ArrayList<>();
            while(rlorp.size() != 0){
                csl_queue.add(new Pair<>((rlorp.size() * resources.get(p).csl), rlorp.size()));
                for(int q = 0; q < rlorp.size(); q++){
                    rlorp.set(q, rlorp.get(q) - 1);
                    if (rlorp.get(q) < 1) {
                        rlorp.remove(q);
                        q--;
                    }
                }
            }
            all_csl_queue.add(csl_queue);
        }

        Map<Set<Integer>, Long> www = new HashMap<>();
        //前面计算了<priority-preemptions>
        // 遍历每个priority，计算当前priority的任务可以抢占的资源index，最后得到<indexList, preempt>
        for (Integer high_task_priority : sortedMap.keySet()) {
            long preempt = sortedMap.get(high_task_priority);

            Set<Integer> rIndexList = new HashSet<>();
            for (int w = 0; w < task.resource_required_index.size(); w++) {
                int rIndex = task.resource_required_index.get(w);
                // 能够抢占的资源的index
                if (high_task_priority > task.resource_required_priority.get(w)) {
                    rIndexList.add(rIndex);
                }
            }
            // 任务\tau_x的高优先级任务 考虑传递性阻塞
            ArrayList<SporadicTask> taskset = tasks.get(task.partition);
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
            if (rIndexList.size() != 0) {   //<indexList, preempt>
                www.put(rIndexList, www.getOrDefault(rIndexList, (long) 0) + preempt);
            }
        }
        // 按照indexList排序
        Map<Set<Integer>, Long> sortedwww = www.entrySet()
                .stream()
                .sorted(Map.Entry.<Set<Integer>, Long>comparingByKey(Comparator.comparingInt(set -> set.size())).reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));

        ArrayList<Pair<Long, Integer>> sum_array = new ArrayList<>();
        if(sortedwww.size() > 0) {
            List<Map.Entry<Set<Integer>, Long>> entryList = new ArrayList<>(sortedwww.entrySet());
            Long sum_preempt = 0L;
            // 迭代过程优化
            for(int i = 0 ; i < entryList.size(); i++){
                Map.Entry<Set<Integer>, Long> A = entryList.get(i);
                Map.Entry<Set<Integer>, Long> B = i == entryList.size() -1 ? null : entryList.get(i+1);
                Set<Integer> A_list = A.getKey();
                Set<Integer> B_list = B == null ? new HashSet<>() : B.getKey();
                Long A_preempt = A.getValue();
                sum_preempt += A_preempt;
                // 创建一个新的集合来存储差集，以便保持原始集合不变
                Set<Integer> differenceSet = new HashSet<>(A_list);
                // 从differenceSet中移除setB中的所有元素，得到差集
                differenceSet.removeAll(B_list);
                // 从differenceSet中索引指示的all_csl_queue中的几个queue合并，并取前sum_preempt个最大的数存入sum_array中
                //ArrayList<Long> sum_list = new ArrayList<>();
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
            /*****overhead需要根据'实际造成的抢占'加入*/
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

    private long getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource,
                                            ArrayList<Long> requestsLeftOnRemoteP, long[][] Ris, long time, boolean btbHit, boolean useRi) {

        // 需要注意的前提是，此函数计算的是针对'单个'资源计算相应的访问&阻塞时间

        long spin = 0;
        long ncs = 0;

        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                // Compute: 访问该资源的所有'本地'高优先级任务在task的响应时间内的访问次数之和，加入ncs(1)。
                ncs += (long) Math.ceil((double) (time + (btbHit ? (useRi ? Ris[task.partition][i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }

        // Compute: 若task也访问该资源，将其访问资源的次数也加入ncs(2)。
        if (task.resource_required_index.contains(resource.id - 1))
            ncs += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));

        // 若ncs不大于0，意味着task和其本地高优先级任务都不访问这个资源，也就不需要计算任务resource相关的访问&阻塞。
        if (ncs > 0) {

            for (int i = 0; i < tasks.size(); i++) {

                if (task.partition != i) {
                    /* 对于每个远程处理器，number_of_request_by_Remote_P表示该处理器(all task)上访问该资源总的次数 */
                    long number_of_request_by_Remote_P = 0;
                    // 遍历该处理器上的任务
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        // 如果任务访问这个资源
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            // int indexR = getIndexRInTask(remote_task, resource);
                            int indexR = remote_task.resource_required_index.indexOf(resource.id - 1);
                            // 在task的响应时间内远程处理器访问该资源的总次数
                            int number_of_release = (int) Math
                                    .ceil((double) (time + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                            number_of_request_by_Remote_P += (long) number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }

                    // 取number_of_request_by_Remote_P和ncs的最小值，作为Direct/InDirect Spin Delay的bound。
                    long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);
                    spin += possible_spin_delay;
                    // 如果该处理器上还有处理次数，更新requestsLeftOnRemoteP。
                    if (number_of_request_by_Remote_P - ncs > 0)
                        requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
                }
            }

        }

        // 每次资源访问都会有额外的lock&unlock的开销，进行相应的记录。
        task.implementation_overheads += (spin + ncs) * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);

        task.blocking_overheads += (spin + ncs
                - (task.resource_required_index.contains(resource.id - 1)
                ? task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1))
                : 0))
                * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);

        // 最终的计算式
        return spin * resource.csl + ncs * resource.csl;
    }


    /***************************************************
     ************* InDirect Spin Delay *******************
     ***************************************************/
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                          double oneMig, long np, boolean btbHit, boolean useRi) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
                t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);
            }
        }
        return interference;
    }


    /***************************************************
     ************** Arrival Blocking *******************
     ***************************************************/
    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time, double oneMig,
                               long np, boolean btbHit, boolean useRi) {
        long fifop_localblocking = FIFOPlocalBlocking(t, tasks, resources, Ris, time, btbHit, useRi);
        t.implementation_overheads += t.fifop_arrivalblocking_overheads;
        t.blocking_overheads += t.fifonp_arrivalblocking_overheads;
        return fifop_localblocking;
    }

    private long FIFOPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri,
                                    boolean btbHit, boolean useRi) {
        ArrayList<ArrayList<Resource>> LocalBlockingResources = FIFOPgetLocalBlockingResources_Dynamic(t, resources, tasks.get(t.partition));
        ArrayList<Resource> shortLocalBlockingResource = LocalBlockingResources.get(0);
        ArrayList<Resource> longLocalBlockingResource = LocalBlockingResources.get(1);
        ArrayList<Pair<Long, Integer>> local_blocking_each_resource = new ArrayList<>();

        for (Resource res : shortLocalBlockingResource) {
            long local_blocking = res.csl;
            local_blocking_each_resource.add(new Pair<>(local_blocking, 1));
        }

        for (Resource res : longLocalBlockingResource) {
            long local_blocking = res.csl * res.partitions.size();
            int cnt = res.partitions.size();
//            for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
//                int partition = res.partitions.get(parition_index);
//                int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi);  //高优先级任务请求次数
//                int norT = t.resource_required_index.contains(res.id - 1)
//                        ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
//                        : 0;
//                int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], Ri, btbHit, useRi);  //远程核心
//
//                if (partition != t.partition && (norHP + norT) < norR) {
//                    local_blocking += res.csl;
//                    cnt+=1;
//                }
//            }
            local_blocking_each_resource.add(new Pair<>(local_blocking, cnt));
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

    /*
     * gives that number of requests from HP local tasks for a resource that is
     * required by the given task.
     */
    private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit, boolean useRi) {
        int number_of_request_by_HP = 0;
        int priority = task.priority;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask hpTask = tasks.get(i);
                int indexR = getIndexRInTask(hpTask, resource);
                number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_HP;
    }

    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit, boolean useRi) {
        int number_of_request_by_Remote_P = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : remote_task.deadline) : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_Remote_P;
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
