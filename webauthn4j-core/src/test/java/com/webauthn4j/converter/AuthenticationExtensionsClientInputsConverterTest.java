/*
 * Copyright 2002-2018 the original author or authors.
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

package com.webauthn4j.converter;

import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.registry.Registry;
import com.webauthn4j.request.extension.client.AuthenticationExtensionsClientInputs;
import com.webauthn4j.request.extension.client.FIDOAppIDExtensionClientInput;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationExtensionsClientInputsConverterTest {

    private AuthenticationExtensionsClientInputsConverter authenticationExtensionsClientInputsConverter = new AuthenticationExtensionsClientInputsConverter(new Registry());


    @Test
    public void convert_null_test() {
        assertThat(authenticationExtensionsClientInputsConverter.convert(null)).isNull();
    }

    @Test
    public void convertToString_test() {
        AuthenticationExtensionsClientInputs clientInputs = new AuthenticationExtensionsClientInputs();
        clientInputs.put(FIDOAppIDExtensionClientInput.ID, new FIDOAppIDExtensionClientInput("test"));
        assertThat(authenticationExtensionsClientInputsConverter.convertToString(clientInputs)).isEqualTo("{\"appid\":\"test\"}");
    }

    @Test
    public void convertToString_null_test() {
        assertThat(authenticationExtensionsClientInputsConverter.convertToString(null)).isNull();
    }

    @Test
    public void convert_test() {
        String source = "{\"appid\":\"dummy\"}";
        AuthenticationExtensionsClientInputs clientInputs = authenticationExtensionsClientInputsConverter.convert(source);
        assertThat(clientInputs.get(FIDOAppIDExtensionClientInput.ID)).isEqualTo(new FIDOAppIDExtensionClientInput("dummy"));
    }

    @Test(expected = DataConversionException.class)
    public void convert_with_invalid_extension_test() {
        String source = "{\"invalid\":\"\"}";
        authenticationExtensionsClientInputsConverter.convert(source);
    }
}
