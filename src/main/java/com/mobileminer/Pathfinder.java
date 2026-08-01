package com.mobileminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Pathfinder {
    private static final Set<BlockPos> SOLID_BLOCK_CACHE = new HashSet<>();
    private static final Set<BlockPos> PASSABLE_BLOCK_CACHE = new HashSet<>();

    public static void cacheWorldChunk(Minecraft client, int radius) {
        if (client.player == null || client.level == null) return;
        BlockPos playerPos = client.player.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = playerPos.offset(x, y, z);
                    BlockState state = client.level.getBlockState(p);

                    if (state.isSolidRender()) {
                        SOLID_BLOCK_CACHE.add(p.immutable());
                        PASSABLE_BLOCK_CACHE.remove(p);
                    } else if (state.isAir() || state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock) {
                        PASSABLE_BLOCK_CACHE.add(p.immutable());
                        SOLID_BLOCK_CACHE.remove(p);
                    }
                }
            }
        }
    }

    public static CompletableFuture<List<BlockPos>> calculatePathAsync(Minecraft client, BlockPos start, BlockPos target) {
        return CompletableFuture.supplyAsync(() -> {
            cacheWorldChunk(client, 16);
            return findPath(client, start, target);
        });
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

            for (BlockPos neighborPos : getNeighbors(current.pos)) {
                if (!isWalkable(client, neighborPos)) continue;

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

    private static boolean isWalkable(Minecraft client, BlockPos pos) {
        boolean feetPassable = PASSABLE_BLOCK_CACHE.contains(pos) || (!SOLID_BLOCK_CACHE.contains(pos) && !client.level.getBlockState(pos).isSolidRender());
        boolean headPassable = PASSABLE_BLOCK_CACHE.contains(pos.above()) || (!SOLID_BLOCK_CACHE.contains(pos.above()) && !client.level.getBlockState(pos.above()).isSolidRender());
        boolean groundSolid = SOLID_BLOCK_CACHE.contains(pos.below()) || (!PASSABLE_BLOCK_CACHE.contains(pos.below()) && client.level.getBlockState(pos.below()).isSolidRender());

        return feetPassable && headPassable && groundSolid;
    }

    private static List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        neighbors.add(pos.north());
        neighbors.add(pos.south());
        neighbors.add(pos.east());
        neighbors.add(pos.west());
        neighbors.add(pos.above());
        neighbors.add(pos.below());
        return neighbors;
    }

    private static class Node {
        BlockPos pos;
        Node parent;
        double gScore;
        double fScore;

        Node(BlockPos pos, Node parent, double gScore, double fScore) {
            this.pos = pos;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }
}
