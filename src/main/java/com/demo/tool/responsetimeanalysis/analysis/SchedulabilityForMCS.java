package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.generator.FlexibleWaitingPriorityAssignment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class SchedulabilityForMCS {
    public static Logger log = LogManager.getLogger();
    public void tasksRefresh(ArrayList<ArrayList<SporadicTask>> tasks) {
        for (ArrayList<SporadicTask> task : tasks) {
            for (SporadicTask sporadicTask : task) {
                sporadicTask.Ri = 0;
                sporadicTask.spin = 0;
                sporadicTask.interference = 0;
                sporadicTask.local = 0;
                sporadicTask.total_blocking = 0;
                sporadicTask.indirect_spin = 0;

                sporadicTask.Ri_LO = 0;
                sporadicTask.Ri_HI = 0;
                sporadicTask.Ri_Switch = 0;

                sporadicTask.PWLP_S = 0;
                sporadicTask.spin_delay_by_preemptions = 0;
                sporadicTask.implementation_overheads = 0;
                sporadicTask.blocking_overheads = 0;
                sporadicTask.mrsp_arrivalblocking_overheads = 0;
                sporadicTask.fifonp_arrivalblocking_overheads = 0;
                sporadicTask.fifop_arrivalblocking_overheads = 0;

                sporadicTask.migration_overheads_plus = 0;
                sporadicTask.np_section = 0;
                sporadicTask.test_delay = 0;
                sporadicTask.schedulable = -1;
            }
        }
    }

    public boolean isSchedulableForLowMode(String rtm, ArrayList<ArrayList<SporadicTask>> tasks,
                                           ArrayList<Resource> resources) {

        for (Resource res : resources) {
            res.csl = res.csl_low;
            res.onlyLow = true;
        }
        for (ArrayList<SporadicTask> task : tasks) {
            for (SporadicTask sporadicTask : task) {
                sporadicTask.WCET = sporadicTask.C_LOW;
                sporadicTask.pure_resource_execution_time = sporadicTask.prec_LOW;
            }
        }

        boolean res = isSchedulableForStableMode(rtm, tasks, resources);
        for (ArrayList<SporadicTask> task : tasks)
            for (SporadicTask sporadicTask : task)
                if (sporadicTask.schedulable == 1) sporadicTask.Ri_LO = sporadicTask.Ri;
        return res;

    }

    public boolean isSchedulableForHighMode(String rtm, ArrayList<ArrayList<SporadicTask>> tasksToAlloc,
                                            ArrayList<Resource> resources) {
        ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();

        for (ArrayList<SporadicTask> sporadicTasks : tasksToAlloc) {
            ArrayList<SporadicTask> temp = new ArrayList<>();
            for (SporadicTask sporadicTask : sporadicTasks) {
                if (sporadicTask.critical == 1)
                    temp.add(sporadicTask);
            }
            tasks.add(temp);
        }
        for (Resource res : resources) {
            res.csl = res.csl_high;
            res.onlyLow = false;
        }
        for (ArrayList<SporadicTask> task : tasks) {
            for (SporadicTask sporadicTask : task) {
                sporadicTask.WCET = sporadicTask.C_HIGH;
                sporadicTask.pure_resource_execution_time = sporadicTask.prec_HIGH;
            }
        }

        boolean res = isSchedulableForStableMode(rtm, tasks, resources);
        for (ArrayList<SporadicTask> task : tasks)
            for (SporadicTask sporadicTask : task)
                if (sporadicTask.schedulable == 1) sporadicTask.Ri_HI = sporadicTask.Ri;
        return res;
    }


    private boolean isSchedulableForStableMode(String rtm, ArrayList<ArrayList<SporadicTask>> tasks,
                                               ArrayList<Resource> resources) {
        if (tasks == null)
            return false;
//        new PriorityGenerator().assignPrioritiesByDM(tasks);
        if (resources != null && resources.size() > 0) {
            for (Resource res : resources) {
                //res.csl
                res.isGlobal = false;
                res.partitions.clear();
                res.requested_tasks.clear();
            }

            /* for each resource */
            for (Resource resource : resources) {
                /* for each partition */
                for (ArrayList<SporadicTask> sporadicTasks : tasks) {
                    /* for each task in the given partition */
                    for (SporadicTask task : sporadicTasks) {
                        task.PWLP_S = 0;
                        if (task.resource_required_index.contains(resource.id - 1)) {
                            resource.requested_tasks.add(task);
                            if (!resource.partitions.contains(task.partition)) {
                                resource.partitions.add(task.partition);
                            }
                        }
                    }
                }
                if (resource.partitions.size() > 1)
                    resource.isGlobal = true;
            }
        }


        long[][] Ris = null;

        log.info(rtm + " for stable mode analysis started");
        switch (rtm) {
            case "MSRP":
                MSRPOriginal msrp = new MSRPOriginal();
                Ris = msrp.getResponseTime(tasks, resources, false);
                break;
            case "MSRPNew":
                MSRPNew msrpNew = new MSRPNew();
                Ris = msrpNew.getResponseTime(tasks, resources, false);
                break;
            case "Mrsp":
                MrsPOriginal mrsp = new MrsPOriginal();
                Ris = mrsp.getResponseTime(tasks, resources, false);
                break;
            case "MrspNew":
                MrspNew Mrsp = new MrspNew();
                Ris = Mrsp.getResponseTime(tasks, resources, true, false);
                //NewMrsPRTAWithMCNP  mrsPNew = new NewMrsPRTAWithMCNP();

                //Ris = mrsPNew.NewMrsPRTATest(tasks, resources, 6,50,false);
                break;
            case "PWLP":
                PWLPNew pwlp = new PWLPNew();
                Ris = pwlp.getResponseTime(tasks, resources, true, false);
                break;
            case "Dynamic":
                DynamicAnalysis dynamic = new DynamicAnalysis();
                //FRAP analysis = new FRAP();
                // assign priority
                FlexibleWaitingPriorityAssignment waiting = new FlexibleWaitingPriorityAssignment();
                waiting.initNp(tasks, resources);
                waiting.intiWaitingByFrequency(tasks,resources);
                waiting.initWaitingPriorityGetSlackWithRateHelp(tasks,resources);
                //Ris = analysis.getResponseTimeByDMPO(tasks,resources,false,1,true,true,false);
                Ris = dynamic.getResponseTimeByDMPO(tasks, resources, 1, true, true, true, true, false);
                break;
        }
        log.info(rtm + " analysis completed");
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                if (tasks.get(i).get(j).deadline < Ris[i][j]) {
                    tasks.get(i).get(j).Ri_LO = Ris[i][j];
                    tasks.get(i).get(j).Ri_HI = Ris[i][j];
                    tasks.get(i).get(j).schedulable = 0;
                    log.info("Analysis result : Unschedulable");
                    return false;
                } else {
                    tasks.get(i).get(j).schedulable = 1;
                }
            }
        }
        log.info("Analysis result : Schedulable");
        return true;
    }

    public boolean isSchedulableForModeSwitch(String rtm, ArrayList<ArrayList<SporadicTask>> tasks,
                                              ArrayList<Resource> resources) {
        if (tasks == null)
            return false;

//        new PriorityGenerator().assignPrioritiesByDM(tasks);

        if (resources != null && resources.size() > 0) {
            for (Resource res : resources) {
                res.isGlobal = false;
                res.partitions.clear();
                res.requested_tasks.clear();
                res.csl = res.csl_high;
            }

            /* for each resource */
            for (Resource resource : resources) {
                /* for each partition */
                for (ArrayList<SporadicTask> sporadicTasks : tasks) {

                    /* for each task in the given partition */
                    for (SporadicTask task : sporadicTasks) {
                        if (task.resource_required_index.contains(resource.id - 1)) {
                            resource.requested_tasks.add(task);
                            if (!resource.partitions.contains(task.partition)) {
                                resource.partitions.add(task.partition);

                                //resource.ceiling.add(resource.getCeilingForProcessor(tasks.get(task.partition)));
                            }
                        }
                    }
                }

                if (resource.partitions.size() > 1)
                    resource.isGlobal = true;
            }
        }

        ArrayList<ArrayList<SporadicTask>> highTasks = new ArrayList<>();
        ArrayList<ArrayList<SporadicTask>> lowTasks = new ArrayList<>();

        for (ArrayList<SporadicTask> task : tasks) {
            ArrayList<SporadicTask> high = new ArrayList<>();
            ArrayList<SporadicTask> low = new ArrayList<>();
            for (SporadicTask sporadicTask : task) {
                sporadicTask.PWLP_S = 0;
                if (sporadicTask.critical == 0) {
                    sporadicTask.WCET = sporadicTask.C_LOW;
                    sporadicTask.pure_resource_execution_time = sporadicTask.prec_LOW;
                    low.add(sporadicTask);
                } else {
                    sporadicTask.WCET = sporadicTask.C_HIGH;
                    sporadicTask.pure_resource_execution_time = sporadicTask.prec_HIGH;
                    high.add(sporadicTask);
                }
            }
            highTasks.add(high);
            lowTasks.add(low);
        }

        long[][] Ris = null;
        log.info(rtm + " for mode switch analysis started");
        switch (rtm) {
            case "MSRP":
                MSRPOriginalForModeSwitch modeSwitch0 = new MSRPOriginalForModeSwitch();
                Ris = modeSwitch0.getResponseTime(highTasks, resources, lowTasks, false);
                break;
            case "Mrsp":
                MrspOriginalForModeSwitch modeSwitch1 = new MrspOriginalForModeSwitch();
                Ris = modeSwitch1.getResponseTime(highTasks, resources, lowTasks, false);
                break;
            case "MrspNew":
                MrspNewForModeSwitch modeSwitch2 = new MrspNewForModeSwitch();
                Ris = modeSwitch2.getResponseTime(highTasks, resources, lowTasks, false);
                break;
            case "PWLP":
                PWLPNewForModeSwitch modeSwitch3 = new PWLPNewForModeSwitch();
                Ris = modeSwitch3.getResponseTime(highTasks, resources, lowTasks, false);
                break;
            case "MSRPNew":
                MSRPNewForModeSwitch modeSwitch4 = new MSRPNewForModeSwitch();
                Ris = modeSwitch4.getResponseTime(highTasks, resources, lowTasks, true);
                break;
            case "Dynamic":
                DynamicAnalysisForModeSwitch modeSwitch5 = new DynamicAnalysisForModeSwitch();
                //FRAPForModeSwitch frap = new FRAPForModeSwitch();
                // assign priority
                //FlexibleWaitingPriorityAssignment waiting = new FlexibleWaitingPriorityAssignment();
                //waiting.initNp(tasks, resources);
                //waiting.intiWaitingByFrequency(tasks,resources);
                //waiting.initWaitingPriorityGetSlackWithRateHelp(tasks,resources);
                //Ris = frap.getResponseTimeByDMPO(highTasks,resources,lowTasks,false,1,true,true,false);
                Ris = modeSwitch5.getResponseTimeByDMPO(highTasks, resources, lowTasks, 1, true, true, true, true, false);
        }

        log.info(rtm + " analysis completed");
        for (int i = 0; i < highTasks.size(); i++) {
            for (int j = 0; j < highTasks.get(i).size(); j++) {
                highTasks.get(i).get(j).Ri_Switch = Ris[i][j];
                if (highTasks.get(i).get(j).deadline < Ris[i][j]) {
                    highTasks.get(i).get(j).schedulable = 0;
                    log.info("Analysis result : Unschedulable");
                    return false;
                } else {
                    highTasks.get(i).get(j).schedulable = 1;
                }
            }
        }

//        for (int i = 0; i < highTasks.size(); i++) {
//            for (int j = 0; j < highTasks.get(i).size(); j++) {
//                highTasks.get(i).get(j).Ri_Switch = Ris[i][j];
//                //System.out.println(highTasks.get(i).get(j).toString());
//                //System.out.println(highTasks.get(i).get(j).Ris());
//            }
//        }
//        System.out.println("yes");
        log.info("Analysis result : Schedulable");
        return true;
    }
}
