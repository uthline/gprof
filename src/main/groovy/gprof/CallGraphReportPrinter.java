/*
 * Copyright 2013 Masato Nagai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gprof;

import java.io.PrintWriter;
import java.util.*;

public class CallGraphReportPrinter implements ReportPrinter<CallGraphReportElement> {

    private static Character SEPARATOR_CHAR = '-';
    private static String COLUMN_SEPARATOR = "  ";

    private String formatCalls(CallGraphReportElement element) {
        if (element.getRecursiveCalls() > 0) {
            return String.format("%d+%d",
                    element.getCalls() - element.getRecursiveCalls(), element.getRecursiveCalls());
        }
        return String.format("%d", element.getCalls());
    }

    @Override
    public void print(List<CallGraphReportElement> elements, PrintWriter writer) {
        print(elements, writer, null);
    }

    @Override
    public void print(List<CallGraphReportElement> elements, PrintWriter writer, Comparator comparator) {
        Map<Long, CallGraphReportElement> graphTable = new HashMap();
        for (CallGraphReportElement entry : elements) {
            graphTable.put(entry.getIndex(), entry);
        }

        List lines = new ArrayList();
        Map<Column, String> header = new HashMap();
        for (Column col : Column.values()) {
            header.put(col, col.toString());
        }
        lines.add(header);
        for (CallGraphReportElement element : elements) {
            if (element.getParents().isEmpty()) {
            } else {
                for (CallGraphReportElement.Parent parent : element.getParents().values()) {
                    if (parent.getIndex() == 0) {
                        lines.add(
                                Utils.hashMap(
                                        Column.INDEX,
                                        "",
                                        Column.TOTAL_TIME_PERCENT,
                                        "",
                                        Column.SELF_TIME,
                                        "",
                                        Column.CHILDREN_TIME,
                                        "",
                                        Column.CALLS,
                                        "",
                                        Column.NAME,
                                        "    <spontaneous>"));
                    } else if (parent.getIndex() == element.getIndex()) {
                        // ignore recursive call
                    } else {
                        CallGraphReportElement parentRef = graphTable.get(parent.getIndex());
                        lines.add(
                                Utils.hashMap(
                                        Column.INDEX,
                                        "",
                                        Column.TOTAL_TIME_PERCENT,
                                        "",
                                        Column.SELF_TIME,
                                        String.format("%.2f", parent.getSelfTime().microseconds()),
                                        Column.CHILDREN_TIME,
                                        String.format("%.2f", parent.getChildrenTime().microseconds()),
                                        Column.CALLS,
                                        String.format("%d/%d", parent.getCalls(), element.getCalls() - element.getRecursiveCalls()),
                                        Column.NAME,
                                        String.format("    %s.%s [%d]",
                                                parentRef.getMethod().getClassName(),
                                                parentRef.getMethod().getMethodName(),
                                                parent.getIndex())));
                    }
                }
            }
            lines.add(
                Utils.hashMap(
                        Column.INDEX,
                        String.format("[%d]", element.getIndex()),
                        Column.TOTAL_TIME_PERCENT,
                        String.format("%.1f", element.getTimePercent()),
                        Column.SELF_TIME,
                        String.format("%.2f", element.getSelfTime().microseconds()),
                        Column.CHILDREN_TIME,
                        String.format("%.2f", element.getChildrenTime().microseconds()),
                        Column.CALLS,
                        formatCalls(element),
                        Column.NAME,
                        String.format("%s.%s [%d]",
                                element.getMethod().getClassName(),
                                element.getMethod().getMethodName(),
                                element.getIndex())));

            for (CallGraphReportElement.Child child : element.getChildren().values()) {
                CallGraphReportElement childRef = graphTable.get(child.getIndex());
                CallGraphReportElement.Parent childParent = childRef.getParents().get(element.getIndex());
                lines.add(
                    Utils.hashMap(
                            Column.INDEX,
                            "",
                            Column.TOTAL_TIME_PERCENT,
                            "",
                            Column.SELF_TIME,
                            String.format("%.2f", childParent.getSelfTime().microseconds()),
                            Column.CHILDREN_TIME,
                            String.format("%.2f", childParent.getChildrenTime().microseconds()),
                            Column.CALLS,
                            String.format("%d/%d", childParent.getCalls(), childRef.getCalls()),
                            Column.NAME,
                            String.format("    %s.%s [%d]",
                                    childRef.getMethod().getClassName(),
                                    childRef.getMethod().getMethodName(),
                                    child.getIndex())));
            }
            lines.add(SEPARATOR_CHAR);
        }

        Map<Column, Integer> columnWidth = new HashMap();
        for (Column col : Column.values()) {
            columnWidth.put(col, 0);
        }
        for (Object line : lines) {
            if (line instanceof Map) {
                Map<Column, String> cols = (Map<Column, String>) line;
                for (Column col : cols.keySet()) {
                    columnWidth.put(col, Math.max(columnWidth.get(col), cols.get(col).length()));
                }
            }
        }
        int rowWidth = 0;
        for (int w : columnWidth.values()) {
            rowWidth += w;
        }
        rowWidth += COLUMN_SEPARATOR.length() * (columnWidth.values().size() - 2); // do not count Column.SPAN

        for (Object line : lines) {
            if (line instanceof Map) {
                Map<Column, String> cols = (Map<Column, String>) line;
                for (Column col : Column.values()) {
                    if (col == Column.SPAN) {
                        continue;
                    }
                    int w = columnWidth.get(col);
                    String format;
                    if (line == header) {
                        format = "%-" + w + "s";
                    } else {
                        switch(col) {
                            case TOTAL_TIME_PERCENT:
                            case SELF_TIME:
                            case CHILDREN_TIME:
                            case CALLS:
                                format = "%" + w + "s";
                                break;
                            default:
                                format = "%-" + w + "s";
                                break;
                        }
                    }
                    cols.put(col, String.format(format, cols.get(col)));
                }
            }
        }

        List<Column> columns =
            Arrays.asList(
                Column.INDEX, Column.TOTAL_TIME_PERCENT,
                Column.SELF_TIME, Column.CHILDREN_TIME, Column.CALLS, Column.NAME);
        String separator = createSeparator(rowWidth);

        for (Object line: lines) {
            if (line instanceof Map) {
                Map<Column, String> cols = (Map<Column, String>) line;
                List vs = new ArrayList(columns.size());
                for (Column col : columns) {
                    vs.add(cols.get(col));
                }
                writer.println(Utils.join(vs, "  "));
            } else if (line == SEPARATOR_CHAR) {
                writer.println(separator);
            }
        }
        writer.flush();
    }

    private String createSeparator(int rowWidth) {
        StringBuilder separatorBuff = new StringBuilder();
        for (int i = 0; i < rowWidth; i++) {
            separatorBuff.append(SEPARATOR_CHAR);
        }
        return separatorBuff.toString();
    }

    public static enum Column {

        INDEX("index"),
        TOTAL_TIME_PERCENT("% time"),
        SELF_TIME("self"),
        CHILDREN_TIME("children"),
        CALLS("calls"),
        NAME("name"),
        SPAN(""); // FIXME ugly..

        private String name;

        Column(String name) {
           this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

    }

}