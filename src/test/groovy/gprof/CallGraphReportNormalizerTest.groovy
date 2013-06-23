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
package gprof

import spock.lang.Specification

@Mixin(TestHelper)
class CallGraphReportNormalizerTest extends Specification {

    def norm(CallTree tree) {
        new CallGraphReportNormalizer().normalize(tree)
    }
    
    def time(long nanotime) {
        new CallTime(nanotime)
    }
    
    def parent(args) {
        def parent = new CallGraphReportElement.Parent(args.index)
        parent.time = time(args.time)
        parent.childrenTime = time(args.childrenTime)
        parent.calls = args.calls
        parent
    }
    
    def spontaneous(args) {
        parent(args + [index: 0])
    }
    
    def child(args) {
        def child = new CallGraphReportElement.Child(args.index)
        child
    }

    def element(args) {
        def ge = new CallGraphReportElement(args.index, args.thread, args.method, args.depth)
        args.children.each { ge.addChild(it) }
        args.parents.each { ge.addParent(it) }
        ge.timePercent = args.timePercent
        ge.time = ge.parents.inject(time(0L)) { sum, i, p -> sum + p.time }
        ge.childrenTime = ge.parents.inject(time(0L)) { sum, i, p -> sum + p.childrenTime }
        ge.calls = ge.parents.inject(0L) { sum, i, p -> sum + p.calls }
        ge.recursiveCalls = ge.parents.findAll { i, p -> p.index == ge.index }
                                      .inject(0L) { sum, i, p -> sum + p.calls }
        ge
    }

    def "Recursive calls are counted apart from non-recursive calls"() {
        when:
        def elements = norm(tree(
            methodCallNode("A", "a", 100,
                methodCallNode("A", "a", 100,
                    methodCallNode("A", "a", 100)
                )
            ),
            methodCallNode("A", "a", 100,
                methodCallNode("A", "a", 100,
                    methodCallNode("A", "a", 100)
                )
            )
        ))
        then:
        def expected = [
            element(
                index: 1,
                thread: thread(),
                method: method("A", "a"),
                depth: 0,
                timePercent: 100,
                parents: [
                    spontaneous(time: 100 * 2, childrenTime: 0, calls: 2),
                    parent(index: 1, time: 100 * 4, childrenTime: 0, calls: 4),
                ],
                children: [],
            )
        ]
        elements == expected
    }

    def "Method calls have the same caller are unified"() {
        when:
        def elements = norm(tree(
            methodCallNode("A", "a", 50 + 100 * 2,
                methodCallNode("A", "b", 100),
                methodCallNode("A", "b", 100),
            ),
            methodCallNode("A", "a", 50 + 100 * 1,
                methodCallNode("A", "b", 100),
            ),
        ))

        then:
        def expected = [
            element(
                index: 1,
                thread: thread(),
                method: method("A", "a"),
                depth: 0,
                timePercent: 100,
                parents: [spontaneous(time: 50 * 2 + 100 * 3, childrenTime: 100 * 3, calls:2)],
                children: [child(index: 2)]
            ),
            element(
                index: 2,
                thread: thread(),
                method: method("A", "b"),
                depth: 1,
                timePercent: 100 * 3 / (50 * 2 + 100 * 3) * 100,
                parents: [parent(index: 1, time: 100 * 3, childrenTime: 0, calls: 3)],
                children: []
            ),
        ]
        elements == expected
    }

}