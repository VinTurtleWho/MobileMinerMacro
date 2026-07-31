package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Pathfinder {

    // 1. ASYNC THREAD GENERATOR (Prevents Pojav from lagging)
    public static CompletableFuture<List<BlockPos>> calculatePathAsync(Minecraft client, BlockPos start, BlockPos target) {
        return CompletableFuture.supplyAsync(() -> {
            return findPath(client, start, target);
        });
    }

    // 2. THE A* MATH ENGINE
    private static List<BlockPos> findPath(Minecraft client, BlockPos start, BlockPos target) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(Node::getFCost));
        HashSet<BlockPos> closedSet = new HashSet<>();
        
        Node startNode = new Node(start, null, 0, getDistance(start, target));
        openSet.add(startNode);
        
        int maxIterations = 3000; // Hard-limit so it never crashes your phone
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            Node current = openSet.poll();
            
            // If we are within 2 blocks of the mob, the path is complete!
            if (current.pos.closerThan(target, 2.0)) {
                return retracePath(startNode, current);
            }
            
            closedSet.add(current.pos);
            
            // Scan surrounding blocks (North, South, East, West)
            for (BlockPos neighborPos : getNeighbors(client, current.pos)) {
                if (closedSet.contains(neighborPos)) continue;
                
                double tentativeGCost = current.gCost + getDistance(current.pos, neighborPos);
                Node neighborNode = new Node(neighborPos, current, tentativeGCost, getDistance(neighborPos, target));
                
                boolean inOpenSet = false;
                for (Node n : openSet) {
                    if (n.pos.equals(neighborPos) && n.gCost <= tentativeGCost) {
                        inOpenSet = true;
                        break;
                    }
                }
                
                if (!inOpenSet) {
                    openSet.add(neighborNode);
                }
            }
        }
        return null; // No path found (Trapped in a box or mob is unreachable)
    }

    // 3. THE BREADCRUMB TRAIL MAKER
    private static List<BlockPos> retracePath(Node startNode, Node endNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = endNode;
        while (current != startNode) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    // 4. MINECRAFT PHYSICS SIMULATOR
    private static List<BlockPos> getNeighbors(Minecraft client, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        
        for (int[] dir : directions) {
            int dx = dir[0];
            int dz = dir[1];
            
            // Check flat walking
            BlockPos next = pos.offset(dx, 0, dz);
            if (isSafe(client, next)) {
                neighbors.add(next);
                continue; 
            }
            
            // Check jumping (up 1 block)
            BlockPos jump = pos.offset(dx, 1, dz);
            if (isSafe(client, jump) && isPassable(client, pos.above(2))) { // Ensure we don't hit our head
                neighbors.add(jump);
            }
            
            // Check dropping (down 1 block)
            BlockPos fall = pos.offset(dx, -1, dz);
            if (isSafe(client, fall) && isPassable(client, next)) {
                neighbors.add(fall);
            }
        }
        return neighbors;
    }

    // Safely check if a block is walkable
    private static boolean isSafe(Minecraft client, BlockPos pos) {
        return isPassable(client, pos) && isPassable(client, pos.above()) && !isPassable(client, pos.below());
    }

    private static boolean isPassable(Minecraft client, BlockPos pos) {
        try {
            if (client.level == null) return false;
            BlockState state = client.level.getBlockState(pos);
            return state.getCollisionShape(client.level, pos).isEmpty(); 
        } catch (Exception e) {
            return false;
        }
    }

    private static double getDistance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    // 5. INTERNAL NODE DATA
    private static class Node {
        BlockPos pos;
        Node parent;
        double gCost, hCost;

        Node(BlockPos pos, Node parent, double gCost, double hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
        }
        double getFCost() { return gCost + hCost; }
    }
}
