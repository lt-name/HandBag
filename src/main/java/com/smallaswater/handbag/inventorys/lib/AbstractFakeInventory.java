package com.smallaswater.handbag.inventorys.lib;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ContainerOpenPacket;
import cn.nukkit.network.protocol.UpdateBlockPacket;
import com.smallaswater.handbag.HandBag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本类引用 SupermeMortal 的 FakeInventories 插件
 * @author SupermeMortal*/
public abstract class AbstractFakeInventory extends ContainerInventory {

    public long id;

    private static final BlockVector3 ZERO = new BlockVector3(0, 0, 0);

    public static final Map<Player, AbstractFakeInventory> OPEN = new ConcurrentHashMap<>();

    public Map<String, List<BlockVector3>> blockPositions = new HashMap<>();
    private String title;

    AbstractFakeInventory(InventoryType type, InventoryHolder holder, String title) {
        super(holder, type);
        this.title = title == null ? type.getDefaultTitle() : title;
    }

    protected UpdateBlockPacket getDefaultPack(int id,BlockVector3 pos){
        UpdateBlockPacket updateBlock = new UpdateBlockPacket();
        updateBlock.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(id, 0);
        updateBlock.flags = UpdateBlockPacket.FLAG_ALL_PRIORITY;
        updateBlock.x = pos.x;
        updateBlock.y = pos.y;
        updateBlock.z = pos.z;
        return updateBlock;

    }

    @Override
    public void onOpen(Player who) {
//        checkForClosed();
        this.viewers.add(who);
        if (OPEN.putIfAbsent(who, this) != null) {
            return;
        }

        List<BlockVector3> blocks = onOpenBlock(who);
        blockPositions.put(who.getName(), blocks);

        onFakeOpen(who, blocks);
    }

    void onFakeOpen(Player who, List<BlockVector3> blocks) {
        BlockVector3 blockPosition = blocks.isEmpty() ? ZERO : blocks.get(0);

        ContainerOpenPacket containerOpen = new ContainerOpenPacket();
        containerOpen.windowId = who.getWindowId(this);
        containerOpen.type = this.getType().getNetworkType();
        containerOpen.x = blockPosition.x;
        containerOpen.y = blockPosition.y;
        containerOpen.z = blockPosition.z;

        who.dataPacket(containerOpen);

        this.sendContents(who);
    }
    /**
     * 玩家开启
     * @param who 玩家
     * @return 方块坐标*/
    protected abstract List<BlockVector3> onOpenBlock(Player who);

    private ExecutorService service = Executors.newSingleThreadExecutor();
    @Override
    public void onClose(Player who) {
        super.onClose(who);
        OPEN.remove(who, this);
        try {
            if (blockPositions.containsKey(who.getName())) {
                List<BlockVector3> blocks = blockPositions.get(who.getName());
                for (int i = 0, size = blocks.size(); i < size; i++) {
                    final int index = i;
                    service.execute(() -> {
                        Vector3 blockPosition = blocks.get(index).asVector3();
                        UpdateBlockPacket updateBlock = new UpdateBlockPacket();
                        updateBlock.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(who.getLevel().getBlock(blockPosition).getFullId());
                        updateBlock.flags = UpdateBlockPacket.FLAG_ALL_PRIORITY;
                        updateBlock.x = blockPosition.getFloorX();
                        updateBlock.y = blockPosition.getFloorY();
                        updateBlock.z = blockPosition.getFloorZ();
                        who.dataPacket(updateBlock);
                    });
                }
            }
        }catch (Exception e){
            this.clearAll();
            e.printStackTrace();

        }

    }

    @Override
    public InventoryHolder getHolder() {
        return holder;
    }

    @Override
    public String getTitle() {
        return title;
    }




}
