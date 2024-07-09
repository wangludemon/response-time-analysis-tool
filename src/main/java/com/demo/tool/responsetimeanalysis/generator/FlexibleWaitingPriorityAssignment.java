package com.demo.tool.responsetimeanalysis.generator;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * final version function
 */
public class FlexibleWaitingPriorityAssignment {

    private double[][] remainRate;   // 用来存储剩余频率

    public void initNp(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources){
        // 全部设置为np
        for (int i=0;i < tasks.size(); i++){
            for (int j=0; j<tasks.get(i).size(); j++){
                SporadicTask t = tasks.get(i).get(j);
                t.resource_required_priority.clear();
                for (int k=0; k< t.resource_required_index.size();k++){
                    t.resource_required_priority.add(998);
                }
            }
        }
    }

    public void intiWaitingByFrequency(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources){
        // 满足定理一设置为 base  使用 N/T 判断
        for (int i = 0; i < tasks.size(); i++){
            for (int j = 0; j < tasks.get(i).size(); j++){
                SporadicTask t = tasks.get(i).get(j);

                // 遍历t 访问的所有资源
                for (int k = 0; k < t.resource_required_index.size(); k++){
                    // 取当前资源    存储的是资源的位置   未考虑 llp
                    int r_index = t.resource_required_index.get(k);
                    if (requestFrequency(t, tasks, resources.get(r_index))){
                        t.resource_required_priority.set(k, t.priority);
                    }
                }

            }
        }

    }

    // 根据 访问频率计算 判断是否能消耗完远程rate
    public boolean requestFrequency(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource){
        double frequency_local = 0;
        double[] frequency_remote = new double[tasks.size()];

        // 计算本地高优先级+task 的访问频率和  用task 周期放大
        for (int i = 0; i < tasks.get(task.partition).size(); i++){
            SporadicTask t = tasks.get(task.partition).get(i);
            if (t.priority >= task.priority){
                if (t.resource_required_index.contains(resource.id - 1)){
                    int index =  t.resource_required_index.indexOf(resource.id - 1);
                    frequency_local += (double) (t.number_of_access_in_one_release.get(index) * task.period) / t.period;
                }
            }else break;

        }

        ArrayList<ArrayList<Double>> rate = new ArrayList<>();

        for (int i = 0; i < tasks.size(); i++){
            ArrayList<Double> rateTemp = new ArrayList<>();

            if (i == task.partition) continue;
            double frequency = 0;

            for (int j = 0; j < tasks.get(i).size(); j++){
                SporadicTask t = tasks.get(i).get(j);

                rateTemp.add((double) (t.period / task.period));

                if (t.resource_required_index.contains(resource.id - 1)){
                    int index =  t.resource_required_index.indexOf(resource.id - 1);

                    frequency += (double) (t.number_of_access_in_one_release.get(index) * task.period) / t.period;

                    frequency += (double) (t.number_of_access_in_one_release.get(index) * task.period * (t.period )) / (t.period * task.period) ;

                }

            }
            frequency_remote[i] = frequency;
            rate.add(rateTemp);
        }


        boolean flag = true;
        // 判断是否大于所有远程的频率
        for (int i = 0; i < tasks.size(); i++) {
            if (i == task.partition) continue;
            if (frequency_local < frequency_remote[i]){
                flag = false;
                break;
            }

        }
        return flag;
    }


    private double[] rateEk(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource){

        double frequency_local = 0;
        double[] rateE = new double[tasks.size()];

        // 计算task & 本地高优先级task的访问频率和
        for (int i = 0; i < tasks.get(task.partition).size(); i++){
            SporadicTask t = tasks.get(task.partition).get(i);
            if (t.priority >= task.priority){ // 大于等于
                if (t.resource_required_index.contains(resource.id - 1)){
                    int index =  t.resource_required_index.indexOf(resource.id - 1);
                    long reduce_period = t.period / 1000;
                    frequency_local += (double) (t.number_of_access_in_one_release.get(index)) / reduce_period;
                }
            }
        }
        rateE[task.partition] = frequency_local;

        for (int i = 0; i < tasks.size(); i++){
            if (i == task.partition) {
                continue;
            }
            double frequency = 0;
            for (int j = 0; j < tasks.get(i).size(); j++){
                SporadicTask t = tasks.get(i).get(j);
                if (t.resource_required_index.contains(resource.id - 1)){
                    int index = t.resource_required_index.indexOf(resource.id - 1);
                    long reduce_period = t.period / 1000;

                    long reduce_deadline = t.deadline / 1000;
                    long reduce_ti_deadline = task.deadline / 1000;
                    frequency += (double) (t.number_of_access_in_one_release.get(index)) / reduce_period;
                    frequency += (double) (t.number_of_access_in_one_release.get(index) * reduce_deadline) / (reduce_ti_deadline * reduce_period) ;
                }
            }
            rateE[i] = frequency;
        }
        return rateE;
    }


    private HashMap<Resource, Double> rateE(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources){
        // 每次调用rateE都会更新
        // 获取所有资源的 在其他处理器上的剩余rate
        // 遍历资源
        HashMap<Resource, Double> rateE_Map = new HashMap<>();
        for(int k = 0; k < resources.size(); k++){
            remainRate[k] = rateEk(task, tasks, resources.get(k));
            // 本地rate
            double rate_local = remainRate[k][task.partition];
            // 遍历远程核心
            double rateE = 0.0;
            for (int i = 0; i < tasks.size(); i++){
                if (i == task.partition){
                    continue;
                }
                rateE += Math.min(rate_local, remainRate[k][i]);
                // 更新剩余的远程rate
                remainRate[k][i] = Math.max(0, remainRate[k][i] - rate_local);
            }
            rateE_Map.put(resources.get(k),rateE);
        }
        return rateE_Map;

    }

    private HashMap<Resource, Double> rateS(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources){
        // version 3

        HashMap<Resource, Double> rateS_Result_Map = new HashMap<>();


        // 资源， 可抢占其的高优先级任务集合
        HashMap<Resource, HashSet<SporadicTask>> preempt_resource = new HashMap<>();

        for(int i = 0; i < tasks.get(task.partition).size(); i++){
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            // 高优先级任务
            if(hpTask.priority > task.priority){


                // 获取其可能对应的"抢占资源" 遍历hp~task
                for(int j = i + 1; j < tasks.get(task.partition).size(); j++){
                    SporadicTask t = tasks.get(task.partition).get(j);
                    if (t.priority >= task.priority){
                        for(int k = 0; k < t.resource_required_index.size(); k++){
                            int rIndex = t.resource_required_index.get(k);

                            if(hpTask.priority > t.resource_required_priority.get(k)){
                                if (preempt_resource.containsKey(resources.get(rIndex))){
                                    preempt_resource.get(resources.get(rIndex)).add(hpTask);
                                }else {
                                    HashSet<SporadicTask> preempt_task = new HashSet<>();
                                    preempt_task.add(hpTask);
                                    preempt_resource.put(resources.get(rIndex), preempt_task);
                                }
                            }
                        }
                    }

                }
            }
        }

        // 遍历每个资源
        for (Map.Entry<Resource, HashSet<SporadicTask>> entry : preempt_resource.entrySet()) {

            Resource res = entry.getKey();

            HashSet<SporadicTask> preempt_task = entry.getValue();
            double rate_S_HP = 0.0;
            double rate_HP = 0.0;

            // 遍历会抢占当前资源的任务集合，对于这个高优先级任务的抢占次数
            for (SporadicTask hpTask : preempt_task) {
                long reduce_hpTask_period = hpTask.period / 1000;

                rate_HP += (double) 1 / reduce_hpTask_period;
            }


            for (int s = 0; s < remainRate[res.id - 1].length; s++) {
                if (s == task.partition) {
                    continue;
                }

                // 根据高优先级任务的抢占获取远程阻塞rate
                rate_S_HP += Math.min(rate_HP, remainRate[res.id - 1][s]);

                remainRate[res.id - 1][s] -= Math.min(rate_HP, remainRate[res.id - 1][s]);

            }

            rateS_Result_Map.put(res, rate_S_HP);

        }
        return rateS_Result_Map;
    }

    // 没有计算Bi得到的slack
    public double getSlack(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long time){
        long C_hat = task.WCET + task.pure_resource_execution_time;
        for (int i = 0; i < tasks.get(task.partition).size(); i++){
            SporadicTask t = tasks.get(task.partition).get(i);
            if (t.priority > task.priority){
                C_hat += (long) ((t.WCET + t.pure_resource_execution_time) * Math.ceil((double) time / t.period));
            }
        }

        remainRate = new double[resources.size()][tasks.size()];
        HashMap<Resource,Double> rateEMap = rateE(task, tasks, resources);
        HashMap<Resource,Double> rateSMap = rateS(task, tasks, resources);


        double E_blocking = 0.0;
        for (Map.Entry<Resource, Double> entry : rateEMap.entrySet()) {
            E_blocking += entry.getValue() * entry.getKey().csl * time;
        }
        double S_blocking = 0.0;
        for (Map.Entry<Resource, Double> entry : rateSMap.entrySet()) {
            S_blocking += entry.getValue() * entry.getKey().csl * time;
        }


        E_blocking /= 1000;
        S_blocking /= 1000;

        double slack = task.deadline - C_hat - E_blocking - S_blocking;
        return slack;
    }


    // 使用map(rate>0) 估计Bi， 返回最大的 资源和Bi
    private HashMap<Resource, Double> getMaxArrivalBlockingByMapRate( SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources){
        // local low-priority task
        ArrayList<SporadicTask> localLowPriorityTasks = new ArrayList<>();

        for (int i = 0; i < tasks.get(task.partition).size(); i++){
            SporadicTask t = tasks.get(task.partition).get(i);
            // 选择本地低优先级任务
            if(t.priority < task.priority){
                localLowPriorityTasks.add(t);
            }
        }
        // 获取阻塞的资源
        ArrayList<ArrayList<Resource>> LocalBlockingResources = getLocalBlockingResources(task, tasks, resources, localLowPriorityTasks);

        long localblocking = 0;
        int max_index = -1;
        boolean shortFlag = false;

        for (int k = 0; k < LocalBlockingResources.get(0).size(); k++){
            Resource resource = LocalBlockingResources.get(0).get(k);
            long blocking = resource.csl;
            if(blocking > localblocking){
                localblocking = blocking;
                max_index = resource.id - 1;
                shortFlag = true;
            }
        }
        for (int k = 0; k < LocalBlockingResources.get(1).size(); k++){
            Resource resource = LocalBlockingResources.get(1).get(k);
            long blocking = resource.csl;
            // 根据剩余rate > 0 判断map的大小
            for (int i = 0; i < tasks.size(); i++){
                if (i == task.partition) continue;

                if (remainRate[resource.id-1][i] > 0)
                    blocking += resource.csl;
            }

            //long blocking = resource.partitions.size() * resource.csl;
            if(blocking > localblocking){
                localblocking = blocking;
                max_index = resource.id - 1;
                shortFlag = false;
            }
        }
        HashMap<Resource, Double> arrival_blocking_resource = new HashMap<>();

        if(shortFlag || max_index == -1){
            return arrival_blocking_resource;
        }


        arrival_blocking_resource.put(resources.get(max_index), (double)localblocking);
        return arrival_blocking_resource;
    }


    // 定理2 最终调整函数，使用E和S等估计Slack, 判断是否slack<Bi
    public void initWaitingPriorityGetSlackWithRateHelp(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources){

        for(int i = 0 ; i < tasks.size(); i++){
            ArrayList<SporadicTask> tasks_partition = tasks.get(i);
            for(int j = 0 ; j < tasks_partition.size(); j++){
                SporadicTask task = tasks_partition.get(j);


                double slack = getSlack(task, tasks, resources, task.deadline);

                // 获取能造成B的所有resource   该函数使用rate获取最大资源
                HashMap<Resource, Double> arrival_blocking_resource = getMaxArrivalBlockingByMapRate(task,tasks,resources);

                // 空集表示 没有阻塞资源或者属于短资源（无法调整优先级）， 继续检查下一个任务
                if(arrival_blocking_resource.isEmpty()){
                    continue;
                }

                int max_ab_index = 0 ;
                double Bi = 0;
                for (Map.Entry<Resource, Double> entry : arrival_blocking_resource.entrySet()) {
                    max_ab_index = entry.getKey().id -1;
                    Bi = entry.getValue() ;
                }
                while( slack < Bi){

                    // 遍历j+1开始, 所有低优先级任务
                    for(int k = j + 1; k < tasks_partition.size(); k++){
                        SporadicTask low_t = tasks_partition.get(k);
                        int rIndex = low_t.resource_required_index.indexOf(max_ab_index);
                        if(rIndex != -1){
                            int target_pri = Math.max(low_t.priority, task.priority - 2);
                            low_t.resource_required_priority.set(rIndex, target_pri);
                        }
                    }

                    slack = getSlack(task, tasks, resources, task.deadline);

                    // 获取能造成B的所有resource   该函数使用rate获取最大资源
                    arrival_blocking_resource = getMaxArrivalBlockingByMapRate(task,tasks,resources);

                    if(arrival_blocking_resource.isEmpty()){
                        break;
                    }

                    for (Map.Entry<Resource, Double> entry : arrival_blocking_resource.entrySet()) {
                        max_ab_index = entry.getKey().id -1;
                        Bi = entry.getValue() ;
                    }
                }
            }
        }
    }


    // 第一个数组存短资源， 第二个长资源    输入为任务，所有任务，所有资源，以及本地低优先级任务
    private ArrayList<ArrayList<Resource>> getLocalBlockingResources(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<SporadicTask> localLowPriorityTasks) {
        ArrayList<Resource> localLongBlockingResources = new ArrayList<>();
        ArrayList<Resource> localShortBlockingResources = new ArrayList<>();
        ArrayList<ArrayList<Resource>> localBlockingResources = new ArrayList<>();

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // 对于低优先级任务，查看最高的等待优先级  没有考虑执行的ck
            // 如果低优先级任务没有访问，则会返回-1

            if (resource.isGlobal) {
                if (resource.getWaitingCeilingForProcessor(localLowPriorityTasks) == -1)
                    continue;
                else if (resource.getWaitingCeilingForProcessor(localLowPriorityTasks) >= task.priority) {
                    localLongBlockingResources.add(resource);
                } else if (resource.getWaitingCeilingForProcessor(localLowPriorityTasks) < task.priority) {
                    localShortBlockingResources.add(resource);
                }
            } else {
                if (resource.getCeilingForProcessor(tasks.get(task.partition)) == -1)
                    continue;
                if (resource.getCeilingForProcessor(tasks.get(task.partition)) >= task.priority)
                    localShortBlockingResources.add(resource);
            }


//            if (resource.isGlobal && resource.getWaitingCeilingForProcessor(localLowPriorityTasks) >= task.priority){
//                localLongBlockingResources.add(resource);
//            } else if(resource.isGlobal && resource.getWaitingCeilingForProcessor(localLowPriorityTasks) < task.priority){
//                localShortBlockingResources.add(resource);  // 如果没有低优先级任务访问 返回-1，资源会被加入这个集合
//            }else if(!resource.isGlobal && resource.getCeilingForProcessor(tasks.get(task.partition)) >= task.priority){
//                localShortBlockingResources.add(resource);
//            }
        }
        localBlockingResources.add(localShortBlockingResources);
        localBlockingResources.add(localLongBlockingResources);
        return localBlockingResources;
    }


}
