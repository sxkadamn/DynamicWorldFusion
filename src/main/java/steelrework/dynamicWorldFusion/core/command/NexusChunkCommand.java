package steelrework.dynamicWorldFusion.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import steelrework.dynamicWorldFusion.core.network.NodeTcpClient;

public final class NexusChunkCommand implements CommandExecutor {
    private final NodeTcpClient nodeClient;

    public NexusChunkCommand(NodeTcpClient nodeClient) {
        this.nodeClient = nodeClient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("Использование: /nexuschunk <targetNodeId> <world> <chunkX> <chunkZ>");
            return true;
        }

        int chunkX;
        int chunkZ;
        try {
            chunkX = Integer.parseInt(args[2]);
            chunkZ = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Параметры chunkX и chunkZ должны быть целыми числами.");
            return true;
        }

        nodeClient.sendChunkFrame(args[0], args[1], chunkX, chunkZ);
        sender.sendMessage("Фрейм чанка поставлен в очередь на отправку.");
        return true;
    }
}

