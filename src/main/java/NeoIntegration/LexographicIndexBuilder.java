package NeoIntegration;

import PageCacheSort.SetIterator;
import PageCacheSort.Sorter;
import bptree.PageProxyCursor;
import bptree.impl.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.io.pagecache.PagedFile;
import org.neo4j.tooling.GlobalGraphOperations;

import java.io.*;
import java.util.*;

/**
 * Created by max on 6/2/15.
 */
public class LexographicIndexBuilder {
    public static final int MAX_K = 3;
    public static final String DB_PATH = "graph.db/";
    public static final String LUBM_INDEX_PATH = "Lexographiclubm50Index.db";
    public static final String INDEX_METADATA_PATH = "LexographicpathIndexMetaData.dat";
    StringBuilder strBulder;
    LinkedList<String> prettyPaths = new LinkedList<>();
    HashMap<Integer, Sorter> sorters = new HashMap<>();
    Map<Integer, NodeTree> indexes = new HashMap<>();
    TreeMap<Long, PathIDBuilder> relationshipMap = new TreeMap<>(); //relationship types to path ids
    TreeMap<Long, PathIDBuilder> k2RelationshipsMap = new TreeMap<>(); //relationship types to path ids
    TreeMap<Long, PathIDBuilder> k3RelationshipsMap = new TreeMap<>(); //relationship types to path ids
    HashMap<Long, Long> k2PathIds = new HashMap<>();
    HashMap<Long, Long> k3PathIds = new HashMap<>();
    SuperFillSortedDisk k2DiskFiller = new SuperFillSortedDisk(4);
    SuperFillSortedDisk k3DiskFiller = new SuperFillSortedDisk(5);
    long currentShortPathID = 1;


    public static void main(String[] args) throws IOException {
        LexographicIndexBuilder indexBuilder = new LexographicIndexBuilder();

        try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(INDEX_METADATA_PATH, false)))) {
            for(int i = 0; i < indexBuilder.indexes.size(); i++){
                out.println(indexBuilder.sorters.get(i+3).keySize + "," + indexBuilder.indexes.get(i+1).rootNodeId + "," + indexBuilder.indexes.get(i+1).disk.COMPRESSION);
                indexBuilder.indexes.get(i+1).disk.shutdown();
            }
        }
        try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("LexographicLUBMLoaderLog" +".txt", false)))) {
            System.out.println("");
            System.out.println("----- K1 Paths ---- ");
            out.println("----- K1 Paths ---- ");
            for (PathIDBuilder builder: indexBuilder.relationshipMap.values()) {
                System.out.println("Path: " + builder.prettyPrint() + " , pathID: " + builder.buildPath());
                out.println("Path: " + builder.prettyPrint() + " , pathID: " + builder.buildPath());
            }
            System.out.println("----- K2 Paths ---- ");
            out.println("----- K2 Paths ---- ");
            for (Long key: indexBuilder.k2RelationshipsMap.keySet()) {
                System.out.println("Path: " + indexBuilder.k2RelationshipsMap.get(key).getPath() + " , pathID: " + key);
                out.println("Path: " + indexBuilder.k2RelationshipsMap.get(key).getPath() + " , pathID: " + key);
            }
            System.out.println("----- K3 Paths ---- ");
            out.println("----- K3 Paths ---- ");
            for (Long key: indexBuilder.k3RelationshipsMap.keySet()) {
                System.out.println("Path: " + indexBuilder.k3RelationshipsMap.get(key).getPath() + " , pathID: " + key);
                out.println("Path: " + indexBuilder.k3RelationshipsMap.get(key).getPath() + " , pathID: " + key);
            }
        }
    }

    public LexographicIndexBuilder() throws IOException {

        sorters.put(3, new Sorter(3));

        long startTime = System.nanoTime();
        enumerateSingleEdges();
        long endTime = System.nanoTime();
        logToFile("Time to enumerate K1 edges(ns): " + (endTime - startTime));
        Sorter sorterK1 = sorters.get(3);
        System.out.println("\nSorting K = 1");

        startTime = System.nanoTime();
        SetIterator k1Iterator = sorterK1.sort();
        endTime = System.nanoTime();
        logToFile("Time to sort K1 edges(ns): " + (endTime - startTime));

        startTime = System.nanoTime();
        NodeTree k1Index = buildIndex(sorterK1, k1Iterator);
        endTime = System.nanoTime();
        logToFile("Time to bulk load K1 edges into index(ns): " + (endTime - startTime));

        indexes.put(1, k1Index);

        if(MAX_K > 1) {
            startTime = System.nanoTime();
            buildK2Paths();
            k2DiskFiller.finish();
            endTime = System.nanoTime();
            logToFile("Time to build K2 edges(ns): " + (endTime - startTime));
            NodeTree k2Index = buildIndex(k2DiskFiller);
            indexes.put(2, k2Index);
        }

        if(MAX_K > 2) {
            startTime = System.nanoTime();
            buildK3Paths();
            k3DiskFiller.finish();
            endTime = System.nanoTime();
            logToFile("Time to build K3 edges(ns): " + (endTime - startTime));
            NodeTree k3Index = buildIndex(k3DiskFiller);
            indexes.put(3, k3Index);
        }
    }

    public NodeTree buildIndex(SuperFillSortedDisk filler) throws IOException {
        System.out.println("Building Index");
        DiskCache sortedDisk = filler.compressedDisk;
        NodeBulkLoader bulkLoader = new NodeBulkLoader(sortedDisk, filler.finalPageID, filler.keyLength);
        NodeTree index = bulkLoader.run();
        File newFile = new File("K" + filler.keyLength + LUBM_INDEX_PATH);
        sortedDisk.pageCacheFile.renameTo(newFile);
        index.disk.pageCacheFile = newFile;
        System.out.println("Done. Root for this index: " + index.rootNodeId);
        logToFile("index K= " + filler.keyLength+ " root: " + index.rootNodeId);
        return index;
    }

    public NodeTree buildIndex(Sorter sorter, SetIterator finalIterator) throws IOException {
        System.out.println("Building Index");
        DiskCache sortedDisk = sorter.getSortedDisk();
        NodeBulkLoader bulkLoader = new NodeBulkLoader(sortedDisk, sorter.finalPageId(), sorter.keySize);
        NodeTree index = bulkLoader.run();
        sortedDisk.pageCacheFile.renameTo(new File(sorter.toString() + LUBM_INDEX_PATH));
        System.out.println("Done. Root for this index: " + index.rootNodeId);
        logToFile("index K= " + sorter.keySize + " root: " + index.rootNodeId);
        return index;
    }

    private void printStats(double count, double totalRels){
        if(strBulder != null){
            int b = strBulder.toString().length();
            for(int i = 0; i < b; i++){System.out.print("\b");}
        }
        strBulder = new StringBuilder();
        strBulder.append("Progress: ").append(count).append("  |  ").append((int) ((count / totalRels) * 100)).append("% complete. Paths: ").append(relationshipMap.size());
        System.out.print("\r" + strBulder.toString());
    }


    private void enumerateSingleEdges() throws IOException {
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(new File(DB_PATH));
        GlobalGraphOperations ggo = GlobalGraphOperations.at(db);
        int count = 0;
        double totalRels;
        try ( Transaction tx = db.beginTx()) {
            totalRels = IteratorUtil.count(ggo.getAllRelationships());
            for(Relationship edge : ggo.getAllRelationships()){
                if (count % 1000 == 0) {
                    printStats(count, totalRels);
                }
                addPath(edge.getStartNode(), edge, edge.getEndNode());
                addPath(edge.getEndNode(), edge, edge.getStartNode());
                count++;
            }
        }
        System.out.println("Keys written: " + count);
    }



    private void buildK2Paths() throws IOException {
        System.out.println("Building K2 Paths");
        int pathCount = 0;
        int k2count = 0;
        long prevK2PathId = 0;
        long[] combinedPath;
        int total = relationshipMap.size() * relationshipMap.size();
        for(long pathIdA : relationshipMap.keySet()){
            for(long pathIdB: relationshipMap.keySet()) {
                System.out.print("\rPaths complete: " + pathCount++ + "/" + total);
                if(!PathIDBuilder.lexographicallyFirst(relationshipMap.get(pathIdA), relationshipMap.get(pathIdB))){
                    continue;
                }

                ArrayList<long[]> entries = new ArrayList<>();
                SearchCursor resultA = indexes.get(1).find(new long[]{pathIdA});
                try (PageProxyCursor cursorA = indexes.get(1).disk.getCursor(resultA.pageID, PagedFile.PF_SHARED_LOCK)) {
                    while (resultA.hasNext(cursorA)) {
                        entries.add(resultA.next(cursorA));
                    }
                }
                for (long[] entry : entries) {
                    SearchCursor resultB = indexes.get(1).find(new long[]{pathIdB, entry[2]});
                    try (PageProxyCursor cursorB = indexes.get(1).disk.getCursor(resultB.pageID, PagedFile.PF_SHARED_LOCK)) {
                        while (resultB.hasNext(cursorB)) {
                            long[] secondPath = resultB.next(cursorB);
                            if (entry[1] == secondPath[2]) {//TODO test if this is correct
                                continue;
                            }
                            PathIDBuilder builder = new PathIDBuilder(relationshipMap.get(entry[0]).getPath(), relationshipMap.get(pathIdB).getPath());
                            if (!k2PathIds.containsKey(builder.buildPath())) {
                                k2PathIds.put(builder.buildPath(), currentShortPathID++);
                                k2RelationshipsMap.put(k2PathIds.get(builder.buildPath()), builder);
                            }
                            long k2PathId = k2PathIds.get(builder.buildPath());
                            combinedPath = new long[]{k2PathId, entry[1], entry[2], secondPath[2]};
                            k2DiskFiller.addKey(combinedPath);
                        }
                    }
                }
            }
        }
    }

    private void buildK3Paths() throws IOException {
        System.out.println("Building K3 Paths");
        int pathCount = 0;
        int k2count = 0;
        long prevK2PathId = 0;
        long[] combinedPath;
        int total = relationshipMap.size() * k2PathIds.size();
        for(long pathIdK1 : relationshipMap.keySet()){
            for(long pathIdK2: k2RelationshipsMap.keySet()) {
                System.out.print("\rPaths complete: " + pathCount++ + "/" + total);
                if(!PathIDBuilder.lexographicallyFirst(relationshipMap.get(pathIdK1), k2RelationshipsMap.get(pathIdK2))){
                    continue;
                }
                ArrayList<long[]> entries = new ArrayList<>();
                SearchCursor resultA = indexes.get(1).find(new long[]{pathIdK1});
                try (PageProxyCursor cursorA = indexes.get(1).disk.getCursor(resultA.pageID, PagedFile.PF_SHARED_LOCK)) {
                    while (resultA.hasNext(cursorA)) {
                        entries.add(resultA.next(cursorA));
                    }
                }
                for (long[] entry : entries) {
                    SearchCursor resultB = indexes.get(2).find(new long[]{pathIdK2, entry[2]});
                    try (PageProxyCursor cursorB = indexes.get(2).disk.getCursor(resultB.pageID, PagedFile.PF_SHARED_LOCK)) {
                        while (resultB.hasNext(cursorB)) {
                            long[] secondPath = resultB.next(cursorB);
                            PathIDBuilder builder = new PathIDBuilder(relationshipMap.get(entry[0]).getPath(), k2RelationshipsMap.get(pathIdK2).getPath());
                            if (!k3PathIds.containsKey(builder.buildPath())) {
                                k3PathIds.put(builder.buildPath(), currentShortPathID++);
                                k3RelationshipsMap.put(k3PathIds.get(builder.buildPath()), builder);
                            }
                            long k3PathId = k3PathIds.get(builder.buildPath());
                            combinedPath = new long[]{k3PathId, entry[1], entry[2], secondPath[2], secondPath[3]};
                            k3DiskFiller.addKey(combinedPath);
                        }
                    }
                }
            }
        }
    }


    private void addPath(Node node1, Relationship relationship1, Node node2) throws IOException {
        PathIDBuilder builder = new PathIDBuilder(node1, relationship1, node2);
        long[] key = new long[]{builder.buildPath(), node1.getId(), node2.getId()};
        sorters.get(3).addUnsortedKey(key);
        updateStats(builder);
    }

    private void updateStats(PathIDBuilder builder){
        if(!relationshipMap.containsKey(builder.buildPath())){
            relationshipMap.put(builder.buildPath(), builder);
        }
    }

    public static void logToFile(String text){
        try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("index_building_times_lexographic.txt", true)))) {
            out.println(text);
            System.out.println(text);
        }catch (IOException e) {
            //exception handling left as an exercise for the reader
        }
    }

}
