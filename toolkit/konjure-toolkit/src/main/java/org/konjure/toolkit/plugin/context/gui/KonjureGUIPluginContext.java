/*
 * MIT License
 *
 * Copyright (c) 2018 Konjure
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.konjure.toolkit.plugin.context.gui;

import org.konjure.toolkit.plugin.context.KonjurePluginContext;

import java.util.Map;

/**
 * @author Connor Hollasch
 * @since 5/15/2018
 */
public class KonjureGUIPluginContext implements KonjurePluginContext
{
    private Map<String, Object> optionSpec;

    public KonjureGUIPluginContext (final Map<String, Object> optionSpec)
    {
        this.optionSpec = optionSpec;
    }

    @Override
    public <T> T getFromKey (final String key)
    {
        if (this.optionSpec.containsKey(key)) {
            return (T) this.optionSpec.get(key);
        }

        return null;
    }

    @Override
    public boolean isInputSpecified (final String key)
    {
        return this.optionSpec.containsKey(key);
    }
}