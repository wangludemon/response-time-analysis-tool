package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import java.util.*;

public class FRAP {
    private ArrayList<ArrayList<Long>> RLOP = null;
    public long[][] getResponseTimeByDMPO(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources,
                                          boolean testSchedulability, int extendCal,
                                          boolean btbHit, boolean useRi, boolean printDebug) {

        // 响应时间分析函数规定：任务的优先级、分配方案、各任务访问资源的优先级已提前确定；
        if (tasks == null)
            return null;

        // 迭代起点:(1)各处理器优先级最高的任务放置于数组最前; (2)各task的time设定为WCET+pure_resource_execution_time
        long[][] init_Ri = AnalysisUtils.initResponseTime(tasks);

        // 将init_Ri拷贝给一个全新的数组response_time
        long[][] response_time = new long[tasks.size()][];
        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }
        AnalysisUtils.cloneList(init_Ri, response_time);
        long np = 0;
        /* 三个条件以退出isEqual循环
         * 1. 在testSchedulability条件下，所有任务的RTA迭代停止于一个定值，并全部小于ddl;
         * 2. 在testSchedulability条件下，某一个任务的RTA大于ddl，直接break;
         * 3. 在!testSchedulability条件下，所有任务的RTA迭代停止于task.RTA > task.ddl * extendCal.
         * */
        long count = 0; // 迭代次数
        boolean isEqual = false, missdeadline = false; //用以退出循环的标志
        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            boolean should_finish = true;
            // 核心迭代计算函数
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
            AnalysisUtils.cloneList(response_time_plus, response_time);
            if (testSchedulability) {
                if (missdeadline)
                    break;
            } else {
                if (should_finish)
                    break;
            }
        }

        // 是否打印迭代信息
        if (printDebug) {
            System.out.println("FIFO Spin Locks Framework after " + count + " times of recursion, we got the response time.");
            AnalysisUtils.printResponseTime(response_time, tasks);
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
        // 计算WCET的Interference
        task.interference = highPriorityInterference(task, tasks, resources, response_time, Ri, oneMig, np, btbHit, useRi);
        // 计算E
        task.spin = FIFOPResourceAccessTime(task, tasks, resources, response_time, Ri, btbHit, useRi);
        // 计算B,S
        task.local = getS$B_Min_C_Max_F(task, tasks, resources, response_time, Ri, btbHit, useRi);

        long newRi = task.Ri = task.WCET + task.spin + task.interference + task.local;

        task.total_blocking = task.spin + task.indirect_spin + task.local - task.pure_resource_execution_time + (long) Math.ceil(task.blocking_overheads);
        if (task.total_blocking < 0) {
            System.err.println("total blocking error: T" + task.id + "   total blocking: " + task.total_blocking);
            System.exit(-1);
        }

        return newRi;
    }

    private long getS$B_Min_C_Max_F(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                    boolean btbHit, boolean useRi) {
        // 用于计算资源相关的访问与阻塞时间项
        long spin = 0;

        // 进一步地，需要计算取消机制带来额外的阻塞（访问资源时的抢占引发）
        // 1. 建立本地高优先级任务的priority-preemptions对儿，在后续将根据抢占的'priority'判断其是否能引发抢占。
        Map<Integer, Long> pri_preempt = new HashMap<>();
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            int high_task_priority = tasks.get(task.partition).get(i).priority;
            if (high_task_priority > task.priority) {
                // 在这个高优先级任务的'优先级'下，抢占次数
                long preemptions = (long) Math.ceil((double)(time) / (double) tasks.get(task.partition).get(i).period);
                pri_preempt.put(high_task_priority,preemptions);
            }
        }

        // 算法开始
        // 1. 需要计算访问各requestsLeftOnRemoteP的csl-Queue；
        // S,B共用，区别点在于，当B取其中一个节点前，必然再受到一个小c的blocking.
        ArrayList<ArrayList<RBTQItem>> all_csl_queue = new ArrayList<>();
        // 自增id
        int id = 0;
        for(int i = 0 ; i < this.RLOP.size(); i++){
            ArrayList<Long> rlorp = new ArrayList<>(this.RLOP.get(i));
            ArrayList<RBTQItem> csl_queue = new ArrayList<>();
            while(rlorp.size() != 0){
                // System.out.println(rlorp.size() * resources.get(i).csl);
                csl_queue.add(new RBTQItem(++id,
                        rlorp.size() * resources.get(i).csl,
                        resources.get(i).id, rlorp.size()));
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
        // System.out.println(all_csl_queue.toString());
        // Set<Integer> BQ_ik_Item_Resource_Index = new HashSet<>();
        // 2. 与S计算相关节点构建所需要的信息
        Map<Set<Integer>, Long> indexList_preempt_S = new LinkedHashMap<>();
        for (Integer high_task_priority : pri_preempt.keySet()) {
            long preempt = pri_preempt.get(high_task_priority);
            Set<Integer> rIndexList = new HashSet<>();
            for (int i = 0; i < task.resource_required_index.size(); i++) {
                int rIndex = task.resource_required_index.get(i);
                if (high_task_priority > task.resource_required_priority.get(i)) {
                    rIndexList.add(rIndex);
                }
            }
            // 任务\tau_x的高优先级任务
            ArrayList<SporadicTask> taskset = tasks.get(task.partition);
            for (int i = 0; i < taskset.size(); i++) {
                SporadicTask high_task = taskset.get(i);
                if (high_task.priority > task.priority) {
                    for (int j = 0; j < high_task.resource_required_index.size(); j++) {
                        int rIndex = high_task.resource_required_index.get(j);
                        if (high_task_priority > high_task.resource_required_priority.get(j)) {
                            rIndexList.add(rIndex);
                        }
                    }
                }
            }
            if (rIndexList.size() != 0) {
//                if(!indexList_preempt_S.containsKey(rIndexList)){
//                    ArrayList<Long> l = new ArrayList<>();
//                    l.add(preempt);
//                    indexList_preempt_S.put(rIndexList,l);
//                }
//                else{
//                    indexList_preempt_S.get(rIndexList).add(preempt);
//                }
                indexList_preempt_S.put(rIndexList, indexList_preempt_S.getOrDefault(rIndexList, (long) 0) + preempt);
            }
        }

        // 3. 与B计算相关节点构建所需要的信息
        ArrayList<Integer> rIndexList_B = new ArrayList<>();
        int partition = task.partition;
        ArrayList<SporadicTask> localTasks = tasks.get(partition);

        ArrayList<Integer> rIndexList_B_Onlyc = new ArrayList<>();

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling
            if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                    && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        // 本地资源必然只有一个小c
                        rIndexList_B_Onlyc.add(resource.id - 1);
                        break;
                    }
                }
            }
            // global resources that are accessed from the partition
            if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                boolean onlyC_Flag = false;
                boolean longb_Flag = false;
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        // 有低优先级任务访问这个资源，至少能造成1个小c的blocking，但是我们还要判断是否是长资源
                        onlyC_Flag = true;
                        int rIndex = LP_task.resource_required_index.indexOf(resource.id - 1);
                        int pri = LP_task.resource_required_priority.get(rIndex);
                        if(pri >= task.priority){
                            longb_Flag = true;
                        }
                        // 如果小于，实际上只能造成一个csl的arrival blocking；
//                        if (pri >= task.priority){
//                            rIndexList_B.add(resource.id - 1);
//                            break;
//                        }
//                        else{
//                            rIndexList_B_Onlyc.add(resource.id - 1);
//                            break;
//                        }
                    }
                }

                if(onlyC_Flag && longb_Flag){
                    rIndexList_B.add(resource.id - 1);
                }else if(onlyC_Flag && !longb_Flag){
                    rIndexList_B_Onlyc.add(resource.id - 1);
                }else if(!onlyC_Flag && longb_Flag){
                    System.out.println("ERROR IN ARRIVAL BLOCKING OF MCMF");
                }

            }
        }

        // 4. 至此，我们已获得构建图所需的所有Info.使用SOLVER求解B+S的最大值.
        Max_F_Min_C_SOLVER maxFMinCSolver = new Max_F_Min_C_SOLVER(task, tasks, resources, all_csl_queue, indexList_preempt_S, rIndexList_B, rIndexList_B_Onlyc);
        return -maxFMinCSolver.Result;

        /* OLD_CODE */
//        Map<Integer, Long> sortedMap = pp.entrySet()
//                .stream()
//                .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        Map.Entry::getValue,
//                        (e1, e2) -> e1,
//                        LinkedHashMap::new
//                ));

//        Map<Set<Integer>, Long> sortedwww = www.entrySet()
//                .stream()
//                .sorted(Map.Entry.<Set<Integer>, Long>comparingByKey(Comparator.comparingInt(set -> set.size())).reversed())
//                .collect(Collectors.toMap(
//                        Map.Entry::getKey,
//                        Map.Entry::getValue,
//                        (e1, e2) -> e1,
//                        LinkedHashMap::new));
//
//        ArrayList<Pair<Long, Integer>> sum_array = new ArrayList<>();
//        if(sortedwww.size() > 0) {
//            List<Map.Entry<Set<Integer>, Long>> entryList = new ArrayList<>(sortedwww.entrySet());
//            Long sum_preempt = 0L;
//            // 迭代过程优化
//            for(int i = 0 ; i < entryList.size(); i++){
//                Map.Entry<Set<Integer>, Long> A = entryList.get(i);
//                Map.Entry<Set<Integer>, Long> B = i == entryList.size() -1 ? null : entryList.get(i+1);
//                Set<Integer> A_list = A.getKey();
//                Set<Integer> B_list = B == null ? new HashSet<>() : B.getKey();
//                Long A_preempt = A.getValue();
//                sum_preempt += A_preempt;
//                // 创建一个新的集合来存储差集，以便保持原始集合不变
//                Set<Integer> differenceSet = new HashSet<>(A_list);
//                // 从differenceSet中移除setB中的所有元素，得到差集
//                differenceSet.removeAll(B_list);
//                // 从differenceSet中索引指示的all_csl_queue中的几个queue合并，并取前sum_preempt个最大的数存入sum_array中
//                ArrayList<Pair<Long, Integer>> sum_list = new ArrayList<>();
//                for (Integer index : differenceSet) {
//                    sum_list.addAll(all_csl_queue.get(index));
//                }
//                // sum_array和sum_list合并
//                sum_array.addAll(sum_list);
//                // 从大到小排序
//
//                if(sum_array.size() > 1 && sum_array.get(0) != null){
//                    // 使用Collections.sort方法和自定义比较器来进行降序排序
//                    Collections.sort(sum_array, new PairComparator<Long, Integer>());
//                }
//
//                // sum_array.sort(Comparator.reverseOrder());
//                sum_array = new ArrayList<>(sum_array.subList(0, (int)Math.min(sum_array.size(),sum_preempt)));
//            }
//            // overhead需要根据'实际造成的抢占'加入
//            task.implementation_overheads += sum_array.size() * (AnalysisUtils.FIFOP_CANCEL);
//            task.blocking_overheads += sum_array.size() * (AnalysisUtils.FIFOP_CANCEL);
//        }
//        if(sum_array.size() > 0){
//            for(Pair<Long, Integer> pair : sum_array) {
//                spin += pair.getKey();
//                task.implementation_overheads += pair.getValue() * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
//                task.blocking_overheads += pair.getValue() * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
//            }
//        }
//        return spin;
    }
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

        this.RLOP = requestsLeftOnRemoteP;
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
//                ncs += (long) Math.ceil((double) (time + (btbHit ? (useRi ? Ris[task.partition][i] : hpTask.deadline) : 0)) / (double) hpTask.period)
//                        * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs += (long) Math.ceil((double) (time) / (double) hpTask.period) * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
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
        }else{
            // Still计算远程处理器个数

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
                    // 如果该处理器上还有处理次数，更新requestsLeftOnRemoteP。
                    if (number_of_request_by_Remote_P > 0)
                        requestsLeftOnRemoteP.add(number_of_request_by_Remote_P);
                }
            }

        }
        // 最终的计算式
        return spin * resource.csl + ncs * resource.csl;
    }

    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                          double oneMig, long np, boolean btbHit, boolean useRi) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += (long) Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
                // t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);
            }
        }
        return interference;
    }


}
