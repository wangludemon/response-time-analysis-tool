package com.demo.tool;

import com.demo.tool.responsetimeanalysis.analysis.SchedulabilityForMCS;
import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.generator.AllocationGeneator;
import com.demo.tool.responsetimeanalysis.generator.PriorityGenerator;
import com.demo.tool.responsetimeanalysis.generator.SystemGenerator;
import com.demo.tool.responsetimeanalysis.utils.Factors;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Analysis {
    public static Logger log = LogManager.getLogger();
    AllocationGeneator allocGenerator = new AllocationGeneator();

    public Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> generateSystem(Factors factors) {

        log.info("System generation started");
        //系统任务生成
        SystemGenerator generator = new SystemGenerator(factors.MIN_PERIOD, factors.MAX_PERIOD, true, factors.TOTAL_PARTITIONS, factors.NUMBER_OF_TASKS, factors.RESOURCE_SHARING_FACTOR, factors.CL_RANGE_LOW, factors.CL_RANGE_HIGH, factors.TOTAL_RESOURCES, factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE, factors.UTILISATION, false);

        log.info("Number of cores: " + factors.TOTAL_PARTITIONS);
        log.info("Number of tasks: " + factors.NUMBER_OF_TASKS);
        log.info("Utilization rate: " + factors.UTILISATION);
        log.info("Period range: " + factors.MIN_PERIOD + " - " + factors.MAX_PERIOD);
        log.info("Number of resources: " + factors.TOTAL_RESOURCES);
        log.info("Resource sharing factor: " + factors.RESOURCE_SHARING_FACTOR);
        log.info("Number of max access to one resource: " + factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE);
        log.info("Critical section range: " + factors.CL_RANGE_LOW + " - " + factors.CL_RANGE_HIGH);
        log.info("Allocation method: " + factors.ALLOCATION);
        log.info("Priority ordering method: " + factors.PRIORITY);


        ArrayList<SporadicTask> tasksToAlloc = null;
        ArrayList<Resource> resources = null;
        while (tasksToAlloc == null) {
            tasksToAlloc = generator.generateTasks(true);
            resources = generator.generateResources();

            generator.generateResourceUsage(tasksToAlloc, resources);

            int allocOK = 0;

            //保证生成的系统任务可成功分配到某个核心
            for (int a = 0; a < 6; a++)
                if (allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, a) != null)
                    allocOK++;

            if (allocOK != 6) tasksToAlloc = null;

        }
        log.info("tasks generated");
        log.info("resources generated");
        ArrayList<ArrayList<SporadicTask>> tasks = null;

        log.info(factors.ALLOCATION + " allocation selected");

        switch (factors.ALLOCATION) {
            case "WF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 0);
            case "BF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 1);
            case "FF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 2);
            case "NF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 3);

        }
        log.info("Task assignment completed");

        switch (factors.PRIORITY) {
            case "DMPO" -> new PriorityGenerator().assignPrioritiesByDM(tasks);
        }
        log.info(factors.PRIORITY + " priority method selected");

        log.info("System generation completed");

        return new Pair<>(tasks, resources);
    }


    public Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> processJsonSystem(String json){
        ArrayList<SporadicTask> tasks = new ArrayList<>();
        ArrayList<Resource> resources = new ArrayList<>();
        ArrayList<ArrayList<SporadicTask>> taskPartition = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            int coreCount = mapper.readTree(json).path("system").path("core_count").asInt();
            tasks = mapper.readValue(mapper.readTree(json).path("tasks").toString(), new TypeReference<ArrayList<SporadicTask>>(){});
            resources = mapper.readValue(mapper.readTree(json).path("resources").toString(), new TypeReference<ArrayList<Resource>>(){});

            /** process partition json includes partition and priority */
            for (int i = 0; i < coreCount; i++){
                taskPartition.add(new ArrayList<>());
            }

            /** ensure partition starts from 0 in json or add allocate method for users **/
            for (int i = 0; i < tasks.size(); i++){
                int partition = tasks.get(i).partition;
                if (partition < coreCount){
                    taskPartition.get(partition).add(tasks.get(i));
                }
            }


            /** process resource request */


            for (SporadicTask task : tasks) {
                int C_h = 0;
                int C_l = 0;
                if (!task.resource_required_index.isEmpty()) {
                    // 做映射  res id 到 resources数组下标
                    for (int i = 0; i < task.resource_required_index.size(); i++){
                        int index = 0;

                        for (int j = 0; j < resources.size(); j++){
                            if (task.resource_required_index.get(i) == resources.get(j).id){
                                index = j;
                                C_l += (int) resources.get(j).csl_low * task.number_of_access_in_one_release.get(i);
                                C_h += (int) resources.get(j).csl_high * task.number_of_access_in_one_release.get(i);
                                resources.get(j).requested_tasks.add(task);
                                if (!resources.get(j).partitions.contains(task.partition)) {
                                    resources.get(j).partitions.add(task.partition);
                                }
                            }
                        }
                        task.resource_required_index.set(i, index);
                    }
                    task.prec_LOW = C_l;
                    task.prec_HIGH = C_h;

                    task.util_LOW = (double) (task.C_LOW + C_l) /task.period;
                    task.util_HIGH = (double) (task.C_HIGH + C_h) /task.period;

                    // 暂时不考虑json错误

                }
            }



        } catch (IOException e) {
            e.printStackTrace();
        }

        return new Pair<>(taskPartition, resources);

    }

    public boolean chooseSystemMode(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, Factors factors) {
        SchedulabilityForMCS mcs = new SchedulabilityForMCS();
        mcs.tasksRefresh(tasks);
        log.info("System Mode:" + factors.SYSTEM_MODE);
        log.info("Analysis Mode: " + factors.ANALYSIS_MODE);
        switch (factors.SYSTEM_MODE) {
            case "LO" -> {
                if (mcs.isSchedulableForLowMode(factors.ANALYSIS_MODE, tasks, resources)) return true;
            }
            case "HI" -> {
                if (mcs.isSchedulableForHighMode(factors.ANALYSIS_MODE, tasks, resources)) return true;
            }
            case "ModeSwitch" -> {
                if (mcs.isSchedulableForModeSwitch(factors.ANALYSIS_MODE, tasks, resources)) return true;
            }
        }
        return false;
    }

    public void analysis(Factors factors, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources) {
        log.info("Analysis started");
        factors.schedulable = chooseSystemMode(tasks, resources, factors);
        log.info("Analysis completed");
//        System.out.println("done");
    }

    /** 批量测试功能 **/
    public List<Map<String, Double>> batchAnalysis(Factors factors, int sysNums, ArrayList<String> methods) {
        log.info("Batch Test started");

        List<Map<String, Double>> results = new ArrayList<>();

        Map<String, Double> lowMap = new HashMap<>();
        Map<String, Double> highMap = new HashMap<>();
        Map<String, Double> switchMap = new HashMap<>();

        for(String method : methods){
            lowMap.put(method, (double)0);
            highMap.put(method, (double)0);
            switchMap.put(method, (double)0);
        }



        for (int i = 0; i < sysNums; i++) {

            var pair = generateSystem(factors);
            var tasks = pair.getFirst();
            var resources = pair.getSecond();

            SchedulabilityForMCS mcs = new SchedulabilityForMCS();
            mcs.tasksRefresh(tasks);

            for(String method : methods){
                if (mcs.isSchedulableForLowMode(method, tasks, resources)) {
                    if (lowMap.containsKey(method)) {
                        lowMap.put(method, lowMap.get(method) + 1);
                    } else {
                        lowMap.put(method, (double)1);
                    }
                }

                if (mcs.isSchedulableForHighMode(method, tasks, resources)) {
                    if (highMap.containsKey(method)) {
                        highMap.put(method, highMap.get(method) + 1);
                    } else {
                        highMap.put(method, (double)1);
                    }
                }

                if (mcs.isSchedulableForModeSwitch(method, tasks, resources)) {
                    if (switchMap.containsKey(method)) {
                        switchMap.put(method, switchMap.get(method) + 1);
                    } else {
                        switchMap.put(method, (double)1);
                    }
                }


            }
        }

        results.add(lowMap);
        results.add(highMap);
        results.add(switchMap);



        for (Map<String , Double> res : results) {
            for (Map.Entry<String, Double> entry : res.entrySet()) {
                String key = entry.getKey();
                Double value = entry.getValue();
                System.out.println(key + " " + value);
                if (sysNums > 0)
                    res.put(key, (double) res.get(key) / sysNums);
            }
        }
        log.info("Batch Test completed");
        return results;
    }


}
