/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *  
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ponysdk.ui.server.basic;

import com.ponysdk.ui.terminal.PropertyKey;
import com.ponysdk.ui.terminal.WidgetType;
import com.ponysdk.ui.terminal.instruction.Update;

public class PFlexTable extends PHTMLTable {

    public PFlexTable() {
        addStyleName("pony-PFlexTable");
        setCellFormatter(new PFlexCellFormatter());
    }

    public PFlexCellFormatter getFlexCellFormatter() {
        return (PFlexCellFormatter) getCellFormatter();
    }

    @Override
    protected WidgetType getType() {
        return WidgetType.FLEX_TABLE;
    }

    public class PFlexCellFormatter extends PCellFormatter {

        public void setColSpan(int row, int column, int colSpan) {
            final Update update = new Update(ID);
            update.setMainPropertyKey(PropertyKey.FLEXTABLE_CELL_FORMATTER);
            update.getMainProperty().setProperty(PropertyKey.ROW, row);
            update.getMainProperty().setProperty(PropertyKey.COLUMN, column);
            update.getMainProperty().setProperty(PropertyKey.SET_COL_SPAN, colSpan);
            getPonySession().stackInstruction(update);
        }

        public void setRowSpan(int row, int column, int rowSpan) {
            final Update update = new Update(ID);
            update.setMainPropertyKey(PropertyKey.FLEXTABLE_CELL_FORMATTER);
            update.getMainProperty().setProperty(PropertyKey.ROW, row);
            update.getMainProperty().setProperty(PropertyKey.COLUMN, column);
            update.getMainProperty().setProperty(PropertyKey.SET_ROW_SPAN, rowSpan);
            getPonySession().stackInstruction(update);
        }
    }
}