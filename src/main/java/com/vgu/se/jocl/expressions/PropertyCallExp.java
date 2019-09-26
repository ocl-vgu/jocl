/**************************************************************************
Copyright 2019 Vietnamese-German-University

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

@author: thian
***************************************************************************/


package com.vgu.se.jocl.expressions;

import java.util.List;
import com.vgu.se.jocl.types.Class;

public class PropertyCallExp extends NavigationCallExp {
    public PropertyCallExp(Variable variable, List<OclExp> qualifiers, String referredAttributeName) {
        super(variable, qualifiers);
        this.referredAttributeName = referredAttributeName;
    }

    private String referredAttributeName;
    private Class referredAttributeClass;

    public String getReferredAttributeName() {
        return referredAttributeName;
    }

    public Class getReferredAttributeClass() {
        return referredAttributeClass;
    }
}
