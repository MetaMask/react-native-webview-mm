import React, { useState } from 'react';
import { StyleSheet, Switch, Text, View } from 'react-native';

import WebView from '@metamask/react-native-webview';

const HTML = `
<!DOCTYPE html>\n
<html>
  <head>
    <title>Alerts</title>
    <meta http-equiv="content-type" content="text/html; charset=utf-8">
    <meta name="viewport" content="width=320, user-scalable=no">
    <style type="text/css">
      body {
        margin: 0;
        padding: 8px;
        font: 62.5% arial, sans-serif;
        background: #ccc;
      }
      #demo {
        margin-top: 8px;
        padding: 8px;
        background: #fff;
        border-radius: 4px;
      }
    </style>
  </head>
  <body>
    <button onclick="showAlert()">Show alert</button>
    <button onclick="showConfirm()">Show confirm</button>
    <button onclick="showPrompt()">Show prompt</button>
    <p id="demo">Result will appear here...</p>    
    <script>
      function showAlert() {
        alert("Hello! I am an alert box!");
        document.getElementById("demo").innerHTML = "Alert dismissed!";
      }
      function showConfirm() {
        var response;
        if (confirm("Press a button!")) {
          response = "You pressed OK on confirm!";
        } else {
          response = "You pressed Cancel on confirm!";
        }
        document.getElementById("demo").innerHTML = response;
      }
      function showPrompt() {
        var message;
        const name = prompt("Please enter your name", "Name");
        if (name !== null) {
          message = "Hello " + name;
        } else {
          message = "You pressed Cancel on prompt!";
        }
        document.getElementById("demo").innerHTML = message;
      }
    </script>
  </body>
</html>
`;

export default function Alerts() {
  const [suppressJavaScriptDialogs, setSuppressJavaScriptDialogs] =
    useState(false);
  const [key, setKey] = useState(0);

  const handleToggle = (value: boolean) => {
    setSuppressJavaScriptDialogs(value);
    setKey((k) => k + 1);
  };

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <Text style={styles.label}>suppressJavaScriptDialogs</Text>
        <Switch
          value={suppressJavaScriptDialogs}
          onValueChange={handleToggle}
        />
        <Text style={styles.value}>
          {suppressJavaScriptDialogs ? 'ON' : 'OFF'}
        </Text>
      </View>
      <View style={styles.webviewContainer}>
        <WebView
          key={key}
          source={{ html: HTML }}
          automaticallyAdjustContentInsets={false}
          suppressJavaScriptDialogs={suppressJavaScriptDialogs}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    marginBottom: 8,
  },
  label: {
    flex: 1,
    fontSize: 12,
    fontWeight: '500',
  },
  value: {
    width: 32,
    fontSize: 11,
    color: '#666',
    textAlign: 'right',
  },
  webviewContainer: {
    flex: 1,
  },
});
