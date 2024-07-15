package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;

import java.util.*;

class Edge {
    int v; // 目标节点
    int next; // 下一条边的索引
    long flow; // 流量
    long cost; // 成本

    Edge(int v, int next, long flow, long cost) {
        this.v = v;
        this.next = next;
        this.flow = flow;
        this.cost = cost;
    }
}

class Node implements Comparable<Node> {
    int u;
    long dis;

    public Node(int u, long dis) {
        this.u = u;
        this.dis = dis;
    }

    @Override
    public int compareTo(Node other) {
        return Long.compare(this.dis, other.dis); // 升序排列，如果你想要降序，可以改为`return Long.compare(other.dis, this.dis);`
    }
}

class PPair {
    public int first;
    public int second;

    public PPair(int first, int second) {
        this.first = first;
        this.second = second;
    }

}


public class Max_F_Min_C_SOLVER {

    // 基本原始信息存储
    private SporadicTask task;
    private ArrayList<ArrayList<SporadicTask>> tasks;
    private ArrayList<Resource> resources;

    public ArrayList<ArrayList<RBTQItem>> all_csl_queue;
    private Map<Set<Integer>, Long> indexList_preempt_S;
    private ArrayList<Integer> rIndexList_B;
    private ArrayList<Integer> rIndexList_B_Onlyc;
    int S,T,tot;
    int node_count = 0;
    int role_count = 0;
    int object_count = 0;
    int object_b_count = 0;
    int object_bc_count = 0;
    int unlimit_edge_start_index = -1;
    long[] h;
    long[] dis;
    int[] vis;
    PPair[] p;

    public Long Result;

    public Max_F_Min_C_SOLVER(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<RBTQItem>> all_csl_queue,
                              Map<Set<Integer>, Long> indexList_preempt_S, ArrayList<Integer> rIndexList_B, ArrayList<Integer> rIndexList_B_Onlyc) {
        this.task = task;
        this.tasks = tasks;
        this.resources = resources;
        this.all_csl_queue = all_csl_queue;
        this.indexList_preempt_S = indexList_preempt_S;
        this.rIndexList_B = rIndexList_B;
        this.rIndexList_B_Onlyc = rIndexList_B_Onlyc;
        this.GraphInit();
    }

    private void GraphInit(){

        // ArrivalBlocking作为Only One的“B角色”节点
        node_count++;
        role_count++;

        // 所有抢占次数作为一个"S角色"节点(indexList_preempt_S.size)
        node_count += indexList_preempt_S.size();
        role_count += indexList_preempt_S.size();

        // 加入rIndexList_B.size的小c“物品”节点，但这些物品仅能被B角色节点的拿
        node_count += rIndexList_B.size();
        object_count += rIndexList_B.size();
        object_b_count += rIndexList_B.size();

        node_count += rIndexList_B_Onlyc.size();
        object_count += rIndexList_B_Onlyc.size();
        object_bc_count += rIndexList_B_Onlyc.size();

        // 所有的RemoteBlockingItem作为一个"物品"节点
        int start = role_count + object_b_count + object_bc_count + 1;
        int end;
        Map<Integer,ArrayList<Integer>> rIndex_RBTQItemIndex = new LinkedHashMap<>();

        ArrayList<RBTQItem> rbtqItems = new ArrayList<>();


        // HashMap, 记录点的OverAll Index和RBTQItem_id的关系
        HashMap<Integer, RBTQItem> pointIndex_Id = new HashMap<>();


        for(int i = 0 ; i < all_csl_queue.size(); i++){
            node_count += all_csl_queue.get(i).size();
            object_count += all_csl_queue.get(i).size();
            rbtqItems.addAll(all_csl_queue.get(i));
            // 应该建立一个rIndex和对应RBTQItem点索引的数组
            end = start + all_csl_queue.get(i).size() - 1;
            ArrayList<Integer> RBTQItemIndex = new ArrayList<>();
            for(int j = start, k = 0; j <= end; j++, k++){
                pointIndex_Id.put(j,all_csl_queue.get(i).get(k));
                RBTQItemIndex.add(j);
            }
            rIndex_RBTQItemIndex.put(i,RBTQItemIndex);
//            if(rIndex_RBTQItemIndex.get(i).size() > 0){
//                System.out.println(1);
//            }
            start = end + 1;
        }
        // unlimit_edge_start_index = node_count;
        // node_count += indexList_preempt_S.size();
        // 一个Source节点，一个Sink节点
        S = node_count + 1;
        T = node_count + 2;
        node_count = node_count + 2;
        // 使用链式前向星结构存储图信息
        int[] head = new int[node_count + 1];
        Arrays.fill(head, -1);

        this.tot = 0;
        this.p = new PPair[node_count + 1];


        // 边数组的最大值
        // Edge[] g = new Edge[2*((role_count-1)*(object_count-object_b_count) + object_b_count * 2 + role_count + object_count)];

        Edge[] g = new Edge[node_count * (node_count - 1)];

        // 加边 [1]~[role_count]为角色节点; [role_count+1] ~ [role_count + object_b_count]为小c物品节点
        // [role_count+object_b_count+1] ~ [role_count+object_count]为RBTQItem物品节点
        // 1. 源点src和B角色的边
        this.addEdge(S,1,1,0, g, head);

        // 2. 源点src和S角色的边，flow应该为抢占次数
        int index_S = 0;
//        if(indexList_preempt_S.size() != 0){
//            System.out.println(1);
//        }
        for (Map.Entry<Set<Integer>, Long> entry : indexList_preempt_S.entrySet()) {
            // System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
            this.addEdge(S,index_S + 2, entry.getValue(),0, g, head);
            index_S++;
        }

        // 3. "角色"与"物品"节点

        // 3.1 B角色与小c物品节点, 小c与RBTQItem的节点
        for(int i = role_count + 1; i <= role_count + object_b_count; i++){
            int rI = rIndexList_B.get(i-role_count-1);
            if (resources.get(rI).onlyLow)
                this.addEdge(1, i , 1, -resources.get(rI).csl_low, g, head);
            else
                this.addEdge(1, i , 1, -resources.get(rI).csl_high, g, head);

            ArrayList<Integer> list = rIndex_RBTQItemIndex.get(rI);
            for(int j = 0 ; j < list.size(); j++){
                this.addEdge(i, list.get(j),1, -rbtqItems.get(list.get(j)- role_count - object_b_count - object_bc_count- 1).getBlocking(), g, head);
                this.addEdge(i, T,1, 0, g, head);
            }
            // 这个小c不能经过S物品节点连到T上，我们需要手动加入一些
            if(rIndex_RBTQItemIndex.get(rI).size() == 0){
                this.addEdge(i, T , 1, 0, g, head);
            }
        }

        for(int i = role_count + object_b_count + 1; i <= role_count + object_b_count + object_bc_count; i++){
            int rI = rIndexList_B_Onlyc.get(i-role_count-object_b_count-1);
            if (resources.get(rI).onlyLow)
                this.addEdge(1, i , 1, -resources.get(rI).csl_low, g, head);
            else
                this.addEdge(1, i , 1, -resources.get(rI).csl_high, g, head);
            this.addEdge(i, T , 1, 0, g, head);
        }

        // 3.2 S角色与RBTQItem的节点
        index_S = 0;
        for (Map.Entry<Set<Integer>, Long> entry : indexList_preempt_S.entrySet()) {
            // System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
            for (Integer element : entry.getKey()) {
                // 得到资源index
                ArrayList<Integer> list = rIndex_RBTQItemIndex.get(element);
                for(int j = 0 ; j < list.size(); j++){
                    this.addEdge(index_S + 2, list.get(j),1, -rbtqItems.get(list.get(j)- role_count - object_b_count - object_bc_count- 1).getBlocking(), g, head);
                }
            }
            index_S++;
        }

        //4. 所有S物品节点与sink节点的边
        for(int i = role_count + object_b_count + object_bc_count + 1; i <= role_count + object_count; i++){
            this.addEdge(i, T,1, 0, g, head);
        }

//        //5. 无穷边？
//        index_S = 0;
//        for (Map.Entry<Set<Integer>, Long> entry : indexList_preempt_S.entrySet()) {
//            // System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
//            this.addEdge(index_S + 2, T, entry.getValue(),0, g, head);
//            index_S++;
//        }


        SPFA(head,g);

        long mxf = 0, mnc = 0;

        while(Dijkstra(head,g)){
            long mnf = Long.MAX_VALUE;
            for(int i = 1 ; i <= node_count; i++){
                h[i] += dis[i];
            }
            for(int i = T; i != S; i = p[i].first){
                mnf = Math.min(mnf, g[p[i].second].flow);
            }
            for(int i = T; i != S; i = p[i].first){
                g[p[i].second].flow -= mnf;
                g[p[i].second ^ 1].flow += mnf;
            }
            mxf += mnf;
            mnc += mnf * h[T];
        }

        this.Result = mnc;
//        System.out.println("mxf = " + mxf);
//        System.out.println("mnc = " + mnc);


        ArrayList<ArrayList<RBTQItem>> Usage = new ArrayList<>();
        for(int i = 0; i < resources.size(); i++){
            Usage.add(new ArrayList<>());
        }

//        for(int i = 1; i <= role_count; i++){
//            for(int j = head[i]; j != -1; j = g[j].next){
//                if(i == 1
//                        && g[j].flow == 0
//                        && g[j].v >= role_count + 1
//                        && g[j].v <= role_count + object_b_count + object_bc_count){
//                    System.out.println("B角色拿取了小c" + g[j].cost);
//                    // 拿了小c之后还拿了啥？
//                    for(int k = head[g[j].v]; k != -1; k = g[k].next){
//                        if(g[k].flow == 0 && g[k].v >= role_count + object_b_count + object_bc_count + 1 && g[k].v <= node_count - 2){
//
//                            Usage.get(pointIndex_Id.get(g[k].v).getResource_id() - 1).add(pointIndex_Id.get(g[k].v));
//                            System.out.println("B角色又拿取了rbtqItem: Cost: " + g[k].cost + "; Id: " +  pointIndex_Id.get(g[k].v).getId() + "; blocking: " + pointIndex_Id.get(g[k].v).getBlocking());
//                        }
//                    }
//                }
//                else if(i > 1
//                        && g[j].flow == 0
//                        && g[j].v >= role_count + object_b_count + object_bc_count + 1
//                        && g[j].v <= node_count - 2){
//                    Usage.get(pointIndex_Id.get(g[j].v).getResource_id() - 1).add(pointIndex_Id.get(g[j].v));
//                    System.out.println("S" + i + "角色拿取了rbtqItem: Cost: " + g[j].cost + "; Id: " +  pointIndex_Id.get(g[j].v).getId() + "; blocking: " + pointIndex_Id.get(g[j].v).getBlocking());
//                }
//            }
//        }

        for(ArrayList<RBTQItem> usa : Usage){
            Collections.sort(usa, new Comparator<RBTQItem>() {
                @Override
                public int compare(RBTQItem p1, RBTQItem p2) {
                    // 从大到小排序
                    int blockingCompare = Long.compare(p2.getBlocking(), p1.getBlocking());
                    if(blockingCompare == 0){
                        return Integer.compare(p1.getId(),p2.getId());
                    }
                    return blockingCompare;
                }
            });
        }

//        for(int i = 0 ; i < Usage.size(); i++){
//            for(int j = 0 ; j < Usage.get(i).size(); j++){
//                Iterator<RBTQItem> iterator = all_csl_queue.get(i).iterator();
//                while (iterator.hasNext()) {
//                    RBTQItem rbtqItem = iterator.next();
//                    iterator.remove();
//                }
//            }
//        }

        for(int i = 0 ; i < all_csl_queue.size(); i++){
            Iterator<RBTQItem> iterator = all_csl_queue.get(i).iterator();
            while (iterator.hasNext()) {
                RBTQItem rbtqItem = iterator.next();
                if(containsRBTQItem(Usage.get(i), rbtqItem)) {
                    iterator.remove();
                }
            }
        }

//        System.out.println("_________________________________________________________");

    }
    private static boolean containsRBTQItem(List<RBTQItem> list, RBTQItem item) {
        for (RBTQItem p : list) {
            if (Objects.equals(p.getId(), item.getId())) {
                return true;
            }
        }
        return false;
    }


    private void addEdge(int u, int v, long flow, long cost, Edge[] g, int[] head){
        // 在边数组中加入一条新边
        g[tot] = new Edge(v, head[u], flow, cost);
        // 头节点引到这条边上，然后++索引
        head[u] = tot++;
        // 反向边 同样加入
        g[tot] = new Edge(u, head[v], 0, -cost);
        head[v] = tot++;
    }

    // 首先寻求单源最短路径
    private void SPFA(int[] head, Edge[] g){
        Queue<Integer> queue = new LinkedList<>();
        h = new long[node_count + 1];
        vis = new int[node_count + 1];
        Arrays.fill(vis,0);
        for(int i = 1; i <= node_count ; i++){
            h[i] = Long.MAX_VALUE;
        }
        h[S] = 0;
        queue.add(S);
        vis[S] = 1;
        while(queue.size() > 0){
            int u = queue.poll(); // 返回并移除
            vis[u] = 0;
            for(int i = head[u]; i != -1; i = g[i].next){
                int v = g[i].v;
                long flow = g[i].flow;
                long cost = g[i].cost;
                if(flow > 0 && h[v] > h[u] + cost){
                    h[v] = h[u] + cost;
                    if(vis[v] == 0){
                        queue.add(v);
                        vis[v] = 1;
                    }
                }
            }
        }
        // System.out.println("SPFAYES");
    }

    private boolean Dijkstra(int[] head, Edge[] g){
        dis = new long[node_count + 1];
        Arrays.fill(dis,Long.MAX_VALUE);
        Arrays.fill(vis,0);
        PriorityQueue<Node> q = new PriorityQueue<>();
        dis[S] = 0;
        q.add(new Node(S,dis[S]));
        while(q.size() > 0){
            int u = q.poll().u;
            if(vis[u] > 0){
                continue;
            }
            vis[u] = 1;
            for(int i = head[u]; i != -1; i = g[i].next){
                int v = g[i].v;
                long flow = g[i].flow;
                long cost = g[i].cost;
                long nc = cost + h[u] -h[v];
                if(flow > 0 && dis[v] > dis[u] + nc){
                    p[v] = new PPair(u,i);
                    dis[v] = dis[u] + nc;
                    q.add(new Node(v, dis[v]));
                }
            }
        }
        return dis[T] < Long.MAX_VALUE;
    }


}


