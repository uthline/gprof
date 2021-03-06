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
package groovyx.gprof;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;

public abstract class Report {

    protected CallTree callTree;

    public Report(CallTree callTree) {
        this.callTree = callTree;
    }
    
    public void prettyPrint() {
        prettyPrint(new PrintWriter(System.out));    
    }

    public void prettyPrint(PrintWriter writer) {
        prettyPrint(Collections.emptyMap(), writer);
    }
    
    public void prettyPrint(Map args) {
        prettyPrint(args, new PrintWriter(System.out));    
    }
    
    public abstract void prettyPrint(Map args, PrintWriter writer);

}
