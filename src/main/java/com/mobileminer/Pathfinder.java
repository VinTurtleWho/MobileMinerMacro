package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Pathfinder {
    private static final Set<BlockPos> SOLID_CACHE = new HashSet<>();
    private static final Set<BlockPos> PASSABLE_CACHE = new HashSet<>();

    public static void cacheWorldChunk(Minecraft client, int radius) {
        if (client.player == null || client.level == null) return;
        BlockPos playerPos = client.player.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 5; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = playerPos.offset(x, y, z);
                    BlockState state = client.level.getBlockState(p);
                    if (state.isSolidRender()) {
                        SOLID_CACHE.add(p.immutable());
                        PASSABLE_CACHE.remove(p);
                    } else if (state.isAir() || state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock) {
                        PASSABLE_CACHE.add(p.immutable());
                        SOLID_CACHE.remove(p);
                    }
                }
            }
        }
    }

    public static CompletableFuture<List<BlockPos>> calculatePathAsync(Minecraft client, BlockPos start, BlockPos target) {
        return CompletableFuture.supplyAsync(() -> findPath(client, start, target));
    }

    private static List<BlockPos> findPath(Minecraft client, BlockPos start, BlockPos target) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<BlockPos, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, start.distSqr(target));
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int nodesSearched = 0;
        int maxNodes = 30000; 

        while (!openSet.isEmpty() && nodesSearched < maxNodes) {
            Node current = openSet.poll();
            nodesSearched++;

            if (current.pos.closerThan(target, 2.0)) {
                List<BlockPos> path = new ArrayList<>();
                Node curr = current;
                while (curr != null) {
                    path.add(0, curr.pos);
                    curr = curr.parent;
                }
                return path;
            }

            for (BlockPos neighborPos : getValidWalkingNeighbors(client, current.pos)) {
                double gScore = current.gScore + 1.0;
                Node neighbor = allNodes.get(neighborPos);

                if (neighbor == null || gScore < neighbor.gScore) {
                    if (neighbor == null) {
                        neighbor = new Node(neighborPos, current, gScore, gScore + neighborPos.distSqr(target));
                        allNodes.put(neighborPos, neighbor);
                    } else {
                        neighbor.parent = current;
                        neighbor.gScore = gScore;
                        neighbor.fScore = gScore + neighborPos.distSqr(target);
                    }
                    openSet.add(neighbor);
                }
            }
        }
        return null;
    }

    private static List<BlockPos> getValidWalkingNeighbors(Minecraft client, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        BlockPos[] dirs = {pos.north(), pos.south(), pos.east(), pos.west()};

        for (BlockPos dir : dirs) {
            if (isPassable(client, dir) && isPassable(client, dir.above())) {
                if (isSolid(client, dir.below())) neighbors.add(dir); 
                else if (isPassable(client, dir.below()) && isSolid(client, dir.below().below())) neighbors.add(dir.below());
                else if (isPassable(client, dir.below()) && isPassable(client, dir.below().below()) && isSolid(client, dir.below().below().below())) neighbors.add(dir.below().below());
            }
            if (isSolid(client, dir) && isPassable(client, dir.above()) && isPassable(client, dir.above().above()) && isPassable(client, pos.above().above())) {
                neighbors.add(dir.above()); 
            }
        }
        return neighbors;
    }

    private static boolean isSolid(Minecraft client, BlockPos pos) {
        if (SOLID_CACHE.contains(pos)) return true;
        if (PASSABLE_CACHE.contains(pos)) return false;
        try { return client.level.getBlockState(pos).isSolidRender(); } 
        catch (Exception e) { return false; }
    }

    private static boolean isPassable(Minecraft client, BlockPos pos) {
        return !isSolid(client, pos);
    }

    private static class Node {
        BlockPos pos; Node parent; double gScore, fScore;
        Node(BlockPos pos, Node parent, double gScore, double fScore) {
            this.pos = pos; this.parent = parent; this.gScore = gScore; this.fScore = fScore;
        }
    }
}
