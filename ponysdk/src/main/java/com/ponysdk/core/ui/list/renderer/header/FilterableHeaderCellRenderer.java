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

package com.ponysdk.core.ui.list.renderer.header;

import com.ponysdk.core.ui.basic.PLabel;
import com.ponysdk.core.ui.basic.PWidget;
import com.ponysdk.core.ui.form.formfield.AbstractFormField;
import com.ponysdk.core.ui.list.FilterListener;
import com.ponysdk.core.ui.list.Sortable;

public class FilterableHeaderCellRenderer extends ComplexHeaderCellRenderer {

    public FilterableHeaderCellRenderer(final String caption, final AbstractFormField<?, ? extends PWidget> formField, final String key) {
        this(caption, formField, key, null);
    }

    public FilterableHeaderCellRenderer(final String caption, final AbstractFormField<?, ? extends PWidget> formField, final String key,
            final FilterListener filterListener) {
        super(caption, formField, key, filterListener);
    }

    @Override
    protected void buildCaption(final String s) {
        caption = new PLabel(s);
        panel.setWidget(0, 0, caption);
    }

    @Override
    public Sortable asSortable() {
        return null;
    }

}
