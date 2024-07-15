package com.demo.tool.responsetimeanalysis.utils;

import com.demo.tool.responsetimeanalysis.entity.SporadicTask;

import java.util.ArrayList;

public class AnalysisUtils {
	
	public static final int EXTEND_CAL = 5;
	public static final int MAX_PRIORITY = 1000;

	/* define how long the critical section can be */
	public enum CS_LENGTH_RANGE {
		LONG_CSLEN, MEDIUM_CS_LEN, Random, SHORT_CS_LEN, VERY_LONG_CSLEN, VERY_SHORT_CS_LEN

	}


	/* define how many resources in the system */
	public static enum RESOURCES_RANGE {
		HALF_PARITIONS, /* partitions / 2 us */
		PARTITIONS, /* partitions us */
		DOUBLE_PARTITIONS, /* partitions * 2 us */
	}

	public static int extendCalForGA = 5;
	public static int extendCalForStatic = 1;

	public static double FIFONP_LOCK = (double) (501 + 259 + 219) / (double) 1000;
	public static double FIFONP_UNLOCK = (double) 602 / (double) 1000;

	private static int FIFOP_DEQUEUE_IN_SCHEDULE = 703;
	private static int FIFOP_RE_REQUEST = 744 + 216;
	public static double FIFOP_CANCEL = (double) (FIFOP_DEQUEUE_IN_SCHEDULE + FIFOP_RE_REQUEST) / (double) 1000;
	public static double FIFOP_LOCK = (double) (744 + 216 + 295) / (double) 1000;


	public static double FIFOP_UNLOCK = (double) 602 / (double) 1000;

	private static int MrsP_HELP_IN_SCHEDULE = 745;
	private static int MrsP_INSERT = 2347;
	public static double MrsP_LOCK = (double) (794 + 259 + 219) / (double) 1000;
	public static double MrsP_UNLOCK = (double) (744 + 65 + 571 + 262) / (double) 1000;

	private static int LINUX_CONTEXT_SWTICH = 965;
	private static int LINUX_SCHED = 845;
	private static int LINUX_SCHED_AWAY = 736;
	private static int LITMUS_COMPLETE = 411;
	private static int LITMUS_RELEASE = 1383;

	private static int PFP_SCHED_CHECK = 492;
	private static int PFP_SCHED_REQUEUE = 603;
	private static int PFP_SCHED_SET_NEXT = 308;
	private static int PFP_SCHED_TAKE_NEXT = 274;
	private static int PFP_SCHEDULER = PFP_SCHED_CHECK + PFP_SCHED_REQUEUE + PFP_SCHED_SET_NEXT + PFP_SCHED_TAKE_NEXT;

	public static double MrsP_PREEMPTION_AND_MIGRATION = (double) (LINUX_SCHED * 2 + PFP_SCHED_CHECK * 2 + MrsP_INSERT + PFP_SCHED_REQUEUE
			+ MrsP_HELP_IN_SCHEDULE + PFP_SCHED_SET_NEXT + LINUX_SCHED_AWAY + LINUX_CONTEXT_SWTICH) / (double) 1000;

	private static int CXS = LINUX_SCHED + LINUX_SCHED_AWAY + LINUX_CONTEXT_SWTICH + PFP_SCHEDULER;
	public static double FULL_CONTEXT_SWTICH1 = (double) (CXS) / (double) 1000;
	public static double FULL_CONTEXT_SWTICH2 = (double) (LITMUS_COMPLETE + CXS * 2 + LITMUS_RELEASE) / (double) 1000;

	public static long[][] initResponseTime(ArrayList<ArrayList<SporadicTask>> tasks) {
		long[][] response_times = new long[tasks.size()][];

		for (int i = 0; i < tasks.size(); i++) {
			ArrayList<SporadicTask> task_on_a_partition = tasks.get(i);
			task_on_a_partition.sort((t1, t2) -> -Integer.compare(t1.priority, t2.priority));

			long[] Ri = new long[task_on_a_partition.size()];

			for (int j = 0; j < task_on_a_partition.size(); j++) {
				SporadicTask t = task_on_a_partition.get(j);
				Ri[j] = t.Ri = t.WCET + t.pure_resource_execution_time;
				t.spin = t.interference = t.local = t.indirect_spin = t.total_blocking = 0;
				t.blocking_overheads = t.implementation_overheads = t.migration_overheads_plus = t.mrsp_arrivalblocking_overheads = t.fifonp_arrivalblocking_overheads = t.fifop_arrivalblocking_overheads = 0;

			}
			response_times[i] = Ri;
		}
		return response_times;
	}

	public boolean isSystemSchedulable(ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris) {
		for (int i = 0; i < tasks.size(); i++) {
			for (int j = 0; j < tasks.get(i).size(); j++) {
				if (tasks.get(i).get(j).deadline < Ris[i][j])
					return false;
			}
		}
		return true;
	}

	public static void cloneList(long[][] oldList, long[][] newList) {
		for (int i = 0; i < oldList.length; i++) {
			System.arraycopy(oldList[i], 0, newList[i], 0, oldList[i].length);
		}
	}

	public boolean isArrayContain(int[] array, int value) {

		for (int j : array) {
			if (j == value)
				return true;
		}
		return false;
	}

	public static void printResponseTime(long[][] Ris, ArrayList<ArrayList<SporadicTask>> tasks) {

		for (int i = 0; i < Ris.length; i++) {
			for (int j = 0; j < Ris[i].length; j++) {
				System.out.println(
						"T" + tasks.get(i).get(j).id + " RT: " + Ris[i][j] + ", P: " + tasks.get(i).get(j).priority + ", D: " + tasks.get(i).get(j).deadline + ", Critical: " + tasks.get(i).get(j).critical
								+ ", S = " + tasks.get(i).get(j).spin + ", L = " + tasks.get(i).get(j).local + ", I = " + tasks.get(i).get(j).interference
								+ ", WCET = " + tasks.get(i).get(j).WCET + ", Resource: " + tasks.get(i).get(j).pure_resource_execution_time + ", B = "
								+ tasks.get(i).get(j).indirect_spin + ", implementation_overheads: " + tasks.get(i).get(j).implementation_overheads);

			}
			System.out.println();
		}
	}

	public void main(String args[]) {
		System.out.println(" FIFO-P Lock:   " + FIFOP_LOCK + "   FIFO-P UNLOCK:   " + FIFOP_UNLOCK + "   RE-REQUEST:   " + FIFOP_CANCEL);
		System.out.println(" FIFO-NP Lock:   " + FIFONP_LOCK + "   FIFO-NP UNLOCK:   " + FIFONP_UNLOCK);
		System.out.println(" MrsP Lock:   " + MrsP_LOCK + "   MrsP UNLOCK:   " + MrsP_UNLOCK + "   MIG:   " + MrsP_PREEMPTION_AND_MIGRATION);
		System.out.println(" CX1:    " + FULL_CONTEXT_SWTICH1 + "   CX2:   " + FULL_CONTEXT_SWTICH2);
	}

}
