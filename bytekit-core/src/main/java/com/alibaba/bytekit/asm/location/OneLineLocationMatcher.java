package com.alibaba.bytekit.asm.location;

import com.alibaba.bytekit.asm.MethodProcessor;
import com.alibaba.bytekit.asm.location.filter.LocationFilter;
import com.alibaba.deps.org.objectweb.asm.Opcodes;
import com.alibaba.deps.org.objectweb.asm.tree.AbstractInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.InsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.LineNumberNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配出单个行
 */
public class OneLineLocationMatcher implements LocationMatcher {

    /**
     * 目标行号
     * -1则代表方法的退出之前
     */
    private Integer targetLine;

    /**
     * 是否在行前插入
     */
    private Boolean isAfterLine;

    /***
     * 匹配第几个出现的行号，从上到下
     * 从1开始计算，-1代表最后出现的行号
     * 为什么需要这个值？因为在kotlin编译出来的字节码中，其行号分布非常乱，为了扩大可插桩的范围，增加这个值
     */
    private Integer lineIdx;

    public OneLineLocationMatcher(int targetLine, boolean isAfterLine, int lineIdx) {
        if (targetLine < -1 || lineIdx < -1) {
            throw new IllegalArgumentException("targetLine and lineIdx must grater than -1");
        }
        if (targetLine == -1 && isAfterLine) {
            //期待观测方法退出之后，是没有意义的
            throw new IllegalArgumentException("when you watch after method exit");
        }
        this.targetLine = targetLine;
        this.isAfterLine = isAfterLine;
        this.lineIdx = lineIdx;
    }

    @Override
    public List<Location> match(MethodProcessor methodProcessor) {
        List<Location> locations = new ArrayList<Location>();
        AbstractInsnNode insnNode = methodProcessor.getEnterInsnNode();
        LocationFilter locationFilter = methodProcessor.getLocationFilter();

        //保存行号出现的序号
        int lineOccurIndex = 0;
        while (insnNode != null) {

            if (targetLine == -1) {
                //匹配方法退出之前，可能会有多个return语句
                //这里为何不使用已有的ExitLocationMatcher呢？因为它返回的是ExitLocation，在具体增强的时候，无法获取到对应的行号标记
                if (insnNode instanceof InsnNode) {
                    InsnNode node = (InsnNode) insnNode;
                    if (matchExit(node)) {
                        boolean filtered = !locationFilter.allow(node, LocationType.EXIT, isAfterLine);
                        Location location = new Location.LineLocation(node, targetLine);
                        location.filtered = filtered;
                        location.whenComplete = isAfterLine;
                        locations.add(location);
                    }
                }
            } else {
                //匹配具体的行
                if (insnNode instanceof LineNumberNode) {
                    LineNumberNode lineNumberNode = (LineNumberNode) insnNode;
                    if (matchLine(lineNumberNode.line) && ++lineOccurIndex == lineIdx) {
                        boolean filtered = !locationFilter.allow(lineNumberNode, LocationType.LINE, isAfterLine);
                        //目前因为如果直接返回lineNumberNode，增强完之后会导致行号丢失，暂时没找到原因，因此取上一个节点
                        Location location = new Location.LineLocation(lineNumberNode.getPrevious(), targetLine);
                        location.filtered = filtered;
                        location.whenComplete = isAfterLine;
                        locations.add(location);
                        //由于会存在多个相同行号的情况，这里只匹配指定的第几个行号
                        break;
                    }
                }
            }

            insnNode = insnNode.getNext();
        }
        return locations;
    }

    private boolean matchLine(int line) {
        return line == targetLine;
    }

    public boolean matchExit(InsnNode node) {
        switch (node.getOpcode()) {
            case Opcodes.RETURN: // empty stack
            case Opcodes.IRETURN: // 1 before n/a after
            case Opcodes.FRETURN: // 1 before n/a after
            case Opcodes.ARETURN: // 1 before n/a after
            case Opcodes.LRETURN: // 2 before n/a after
            case Opcodes.DRETURN: // 2 before n/a after
                return true;
        }
        return false;
    }

}
