import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { CallScreen } from './src/components/CallScreen';
import { IncomingCall } from './src/components/IncomingCall';
import { HomeScreen } from './src/screens/HomeScreen';

export type RootStackParamList = {
  Home: undefined;
  Call: {
    targetUserIds?: string[];
    sessionType?: 'video' | 'audio' | 'live';
    roomName?: string;
  };
  IncomingCall: {
    invite: any;
  };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Phoenix RTC 主应用
 */
export default function App() {
  return (
    <SafeAreaProvider>
      <NavigationContainer>
        <Stack.Navigator
          initialRouteName="Home"
          screenOptions={{
            headerStyle: { backgroundColor: '#111' },
            headerTintColor: '#fff',
            headerBackTitleVisible: false,
          }}
        >
          <Stack.Screen
            name="Home"
            component={HomeScreen}
            options={{ title: 'Phoenix RTC' }}
          />
          <Stack.Screen
            name="Call"
            component={CallScreen}
            options={{ headerShown: false }}
          />
          <Stack.Screen
            name="IncomingCall"
            component={IncomingCall}
            options={{ headerShown: false }}
          />
        </Stack.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
