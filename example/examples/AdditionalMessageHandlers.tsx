import React, { Component } from 'react';
import { Text, View } from 'react-native';

import WebView from '@metamask/react-native-webview';

type Props = {};
type State = { messages: string[] };

// A page that posts to its own handler name instead of ReactNativeWebView,
// the way third-party checkout pages do. With enableApplePay the WebView
// removes every injected script, so only a native handler can receive it.
const HTML = `
<!doctype html>
<html>
  <body style="font-family: -apple-system; padding: 16px">
    <h3>additionalMessageHandlerNames</h3>
    <button id="send" style="font-size: 18px; padding: 12px">
      Post to cbOnramp
    </button>
    <script>
      document.getElementById('send').onclick = function () {
        var handlers = window.webkit && window.webkit.messageHandlers;
        if (handlers && handlers.cbOnramp) {
          handlers.cbOnramp.postMessage(JSON.stringify({ eventName: 'example.string' }));
          handlers.cbOnramp.postMessage({ eventName: 'example.object' });
        }
      };
    </script>
  </body>
</html>
`;

export default class AdditionalMessageHandlers extends Component<Props, State> {
  state: State = { messages: [] };

  render() {
    return (
      <View style={{ flex: 1 }}>
        <View style={{ height: 200 }}>
          <WebView
            enableApplePay={true}
            additionalMessageHandlerNames={['cbOnramp']}
            source={{ html: HTML }}
            onMessage={(event) =>
              this.setState((state) => ({
                messages: [...state.messages, event.nativeEvent.data],
              }))
            }
          />
        </View>
        <Text style={{ padding: 16 }}>Received on onMessage:</Text>
        {this.state.messages.map((message, index) => (
          <Text key={index} style={{ paddingHorizontal: 16 }}>
            {message}
          </Text>
        ))}
      </View>
    );
  }
}
