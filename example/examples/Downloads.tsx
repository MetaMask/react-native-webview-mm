import React, { useState } from 'react';
import { Alert, Platform, StyleSheet, Switch, Text, View } from 'react-native';

import WebView, { FileDownload } from '@metamask/react-native-webview';

const HTML = `
<!DOCTYPE html>\n
<html>
  <head>
    <title>Downloads</title>
    <meta http-equiv="content-type" content="text/html; charset=utf-8">
    <meta name="viewport" content="width=320, user-scalable=no">
    <style type="text/css">
      body {
        margin: 0;
        padding: 8px;
        font: 62.5% arial, sans-serif;
        background: #ccc;
      }
      a {
        display: block;
        margin: 8px 0;
      }
    </style>
  </head>
  <body>
    <a href="https://www.7-zip.org/a/7za920.zip">Example zip file download</a>
    <a href="http://test.greenbytes.de/tech/tc2231/attwithisofn2231iso.asis">Download file with non-ascii filename: "foo-ä.html"</a>
  </body>
</html>
`;

export default function Downloads() {
  const [allowFileDownloads, setAllowFileDownloads] = useState(true);
  const [key, setKey] = useState(0);

  const onFileDownload = ({ nativeEvent }: { nativeEvent: FileDownload }) => {
    Alert.alert('File download detected', nativeEvent.downloadUrl);
  };

  const handleToggle = (value: boolean) => {
    setAllowFileDownloads(value);
    setKey((k) => k + 1);
  };

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <Text style={styles.label}>allowFileDownloads</Text>
        <Switch value={allowFileDownloads} onValueChange={handleToggle} />
        <Text style={styles.value}>{allowFileDownloads ? 'ON' : 'OFF'}</Text>
      </View>
      <View style={styles.webviewContainer}>
        <WebView
          key={key}
          source={{ html: HTML }}
          automaticallyAdjustContentInsets={false}
          allowFileDownloads={allowFileDownloads}
          onFileDownload={Platform.OS === 'ios' ? onFileDownload : undefined}
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
