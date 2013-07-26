package com.relativitas.maven.plugins.formatter;

/*
 * Copyright 2010. All work is copyrighted to their respective author(s),
 * unless otherwise stated.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import junit.framework.TestCase;

/**
 * Test class for {@link FormatterMojo}.
 * 
 * @author Matt Blanchette
 */
public class FormatterMojoTest extends TestCase {

    /**
     * Test successfully determining CRLF line ending.
     * 
     * @throws Exception
     */
    public void test_success_read_line_endings_crlf() throws Exception {
        String fileData = "Test\r\nTest\r\nTest\r\n";
        FormatterMojo mojo = new FormatterMojo();
        String lineEnd = mojo.determineLineEnding(fileData);
        assertEquals(FormatterMojo.LINE_ENDING_CRLF_CHARS, lineEnd);
    }

    /**
     * Test successfully determining LF line ending.
     * 
     * @throws Exception
     */
    public void test_success_read_line_endings_lf() throws Exception {
        String fileData = "Test\nTest\nTest\n";
        FormatterMojo mojo = new FormatterMojo();
        String lineEnd = mojo.determineLineEnding(fileData);
        assertEquals(FormatterMojo.LINE_ENDING_LF_CHAR, lineEnd);
    }

    /**
     * Test successfully determining CR line ending.
     * 
     * @throws Exception
     */
    public void test_success_read_line_endings_cr() throws Exception {
        String fileData = "Test\rTest\rTest\r";
        FormatterMojo mojo = new FormatterMojo();
        String lineEnd = mojo.determineLineEnding(fileData);
        assertEquals(FormatterMojo.LINE_ENDING_CR_CHAR, lineEnd);
    }

    /**
     * Test successfully determining LF line ending with mixed endings.
     * 
     * @throws Exception
     */
    public void test_success_read_line_endings_mixed_lf() throws Exception {
        String fileData = "Test\r\nTest\rTest\nTest\nTest\r\nTest\n";
        FormatterMojo mojo = new FormatterMojo();
        String lineEnd = mojo.determineLineEnding(fileData);
        assertEquals(FormatterMojo.LINE_ENDING_LF_CHAR, lineEnd);
    }

    /**
     * Test successfully determining AUTO line ending with mixed endings and no
     * clear majority.
     * 
     * @throws Exception
     */
    public void test_success_read_line_endings_mixed_auto() throws Exception {
        String fileData = "Test\r\nTest\r\nTest\nTest\nTest\r\nTest\nTest\r";
        FormatterMojo mojo = new FormatterMojo();
        String lineEnd = mojo.determineLineEnding(fileData);
        assertNull("Not AUTO line ending", lineEnd);
    }

    /**
     * Test successfully determining AUTO line ending with no endings.
     * 
     * @throws Exception
     */
    public void test_success_read_line_endings_none_auto() throws Exception {
        String fileData = "TestTestTestTest";
        FormatterMojo mojo = new FormatterMojo();
        String lineEnd = mojo.determineLineEnding(fileData);
        assertNull("Not AUTO line ending", lineEnd);
    }

}
